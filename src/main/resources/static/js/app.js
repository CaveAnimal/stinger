
let currentPath = '';
let historyStack = [];
let forwardStack = [];

document.addEventListener('DOMContentLoaded', function() {
    const pathInput = document.getElementById('currentPath');
    const navigateBtn = document.getElementById('navigateBtn');
    const analyzeBtn = document.getElementById('analyzeBtn');
    const backBtn = document.getElementById('backBtn');

    // Set default path to user home or common location
    if (navigator.platform.toLowerCase().includes('win')) {
        pathInput.value = 'C:\\';
    } else {
        pathInput.value = '/home';
    }
    currentPath = pathInput.value;

    navigateBtn.addEventListener('click', () => {
        goToPath(pathInput.value);
    });

    analyzeBtn.addEventListener('click', () => {
        currentPath = pathInput.value;
        analyzeDirectoryStream(currentPath);
    });

    backBtn.addEventListener('click', () => {
        goBack();
    });

    pathInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            goToPath(pathInput.value);
        }
    });

    // Load initial directory
    goToPath(currentPath, false);
});

function goToPath(path, pushHistory = true) {
    if (pushHistory && currentPath && currentPath !== path) {
        historyStack.push(currentPath);
        if (historyStack.length > 20) historyStack.shift(); // limit history
        // updateHistoryList is intentionally kept as a safe no-op when UI isn't present
        updateHistoryList();
    }
    currentPath = path;
    document.getElementById('currentPath').value = path;
    loadDirectory(path);
}

function goBack() {
    if (historyStack.length > 0) {
        const prevPath = historyStack.pop();
        forwardStack.push(currentPath);
        goToPath(prevPath, false);
        updateHistoryList();
    }
}

function updateHistoryList() {
    // Intentionally safe: the visible history UI may be removed. If the container exists, render it,
    // otherwise do nothing (we still maintain the internal historyStack for Back button functionality).
    const historyList = document.getElementById('historyList');
    if (!historyList) return; // UI not present => no DOM operations

    historyList.innerHTML = '';
    historyStack.slice().reverse().forEach((path, idx) => {
        const li = document.createElement('li');
        li.textContent = path;
        li.className = 'history-item';
        li.onclick = () => {
            goToPath(path);
        };
        historyList.appendChild(li);
    });
}

async function loadDirectory(path) {
    const fileList = document.getElementById('fileList');
    fileList.innerHTML = '<div class="loading">Loading directory</div>';

    try {
        const response = await fetch(`/api/list?path=${encodeURIComponent(path)}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Failed to load directory');
        }

        displayFiles(data.files, data.path);
    } catch (error) {
        fileList.innerHTML = `<div class="error-message">Error: ${error.message}</div>`;
    }
}

function displayFiles(files, path) {
    const fileList = document.getElementById('fileList');

    if (files.length === 0) {
        fileList.innerHTML = '<p class="empty-state">This directory is empty</p>';
        return;
    }

    // Sort: directories first, then files
    files.sort((a, b) => {
        if (a.directory && !b.directory) return -1;
        if (!a.directory && b.directory) return 1;
        return a.name.localeCompare(b.name);
    });

    let html = '';

    // Add parent directory option if not at root
    if (path !== '/' && path !== 'C:\\' && path !== 'D:\\') {
        html += `
            <div class="file-item" id="parentDirItem" data-path="${escapeHtml(path)}">
                <span class="file-icon">⬆️</span>
                <div class="file-info">
                    <div class="file-name">..</div>
                    <div class="file-type">Parent Directory</div>
                </div>
            </div>
        `;
    }

    files.forEach(file => {
        const icon = file.directory ? '📁' : getFileIcon(file.type);
        const typeLabel = file.directory ? 'Directory' : capitalizeFirst(file.type);
        // Use data-path attribute to store the path exactly
        html += `
            <div class="file-item"${file.directory ? ` data-path="${escapeHtml(file.path)}" data-directory="true"` : ''}>
                <span class="file-icon">${icon}</span>
                <div class="file-info">
                    <div class="file-name">${escapeHtml(file.name)}</div>
                    <div class="file-type">${typeLabel}${!file.directory && file.size ? ` - ${formatBytes(file.size)}` : ''}</div>
                </div>
            </div>
        `;
    });

    fileList.innerHTML = html;

    // Add event delegation for folder navigation
    fileList.querySelectorAll('.file-item[data-directory="true"]').forEach(item => {
        item.addEventListener('click', function() {
            const path = this.getAttribute('data-path');
            if (path) navigateToDirectory(path);
        });
    });

    // Add event listener for parent directory
    const parentItem = document.getElementById('parentDirItem');
    if (parentItem) {
        parentItem.addEventListener('click', function() {
            const path = this.getAttribute('data-path');
            if (path) navigateToParent(path);
        });
    }
}

function getFileIcon(type) {
    switch(type) {
        case 'code': return '💻';
        case 'document': return '📄';
        default: return '📄';
    }
}

function navigateToDirectory(path) {
    goToPath(path);
}

function navigateToParent(path) {
    const separator = path.includes('\\') ? '\\' : '/';
    const parts = path.split(separator).filter(p => p);
    parts.pop();

    let parentPath;
    if (parts.length === 0) {
        parentPath = separator;
    } else if (parts.length === 1 && /^[A-Za-z]:$/.test(parts[0])) {
        // Drive letter only (e.g. 'C:') -> return 'C:\\' (root of drive)
        parentPath = parts[0] + separator;
    } else {
        parentPath = parts.join(separator);
        if (path.startsWith(separator)) {
            parentPath = separator + parentPath;
        }
    }
    
    navigateToDirectory(parentPath);
}

async function analyzeDirectory(path) {
    const resultsDiv = document.getElementById('analysisResults');
    // smaller, more compact inline loading indicator (reduce wasted vertical space)
    resultsDiv.innerHTML = '<div class="loading compact">Analyzing…</div>';

    try {
        const response = await fetch('/api/analyze', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ path: path })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Failed to analyze directory');
        }

        displayAnalysisResults(data);
    } catch (error) {
        resultsDiv.innerHTML = `<div class="error-message">Error: ${error.message}</div>`;
    }
}

function analyzeDirectoryStream(path) {
    const resultsDiv = document.getElementById('analysisResults');
    resultsDiv.innerHTML = `
        <div id="analysisLive" class="analysis-live">
            <div class="analysis-live-header">Processing files and folders</div>

            <section id="foldersSection" class="analysis-section folders">
                <div class="section-title">Folders — <span id="currentFolderName">(none)</span></div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="folderProgressFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="folderPercent" class="progress-percent">0%</div>
                </div>
                <div id="folderCount" class="progress-count">0 / 0</div>
            </section>

            <section id="codeFilesSection" class="analysis-section code-files">
                <div class="section-title">Code Files — <span id="currentCodeFile">(none)</span></div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="codeFileProgressFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="codeFilePercent" class="progress-percent">0%</div>
                </div>
                <div id="codeFileCount" class="progress-count">0 / 0</div>
                <div class="section-subtitle">Lines of Code</div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="codeLinesProgressFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="codeLinesPercent" class="progress-percent">0%</div>
                </div>
                <div id="codeLinesCount" class="progress-count">0 / 0</div>
            </section>

            <section id="methodsSection" class="analysis-section methods">
                <div class="section-title">Methods — <span id="currentMethod">(none)</span></div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="methodsProgressFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="methodsPercent" class="progress-percent">0%</div>
                </div>
                <div id="methodsCount" class="progress-count">0 / 0</div>
            </section>

            <section id="docFilesSection" class="analysis-section docs">
                <div class="section-title">Doc Files — <span id="currentDocFile">(none)</span></div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="docFileProgressFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="docFilePercent" class="progress-percent">0%</div>
                </div>
                <div id="docFileCount" class="progress-count">0 / 0</div>
                <div class="section-subtitle">Lines of Documents</div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="docLinesProgressFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="docLinesPercent" class="progress-percent">0%</div>
                </div>
                <div id="docLinesCount" class="progress-count">0 / 0</div>
            </section>

            <section id="totalsSection" class="analysis-section totals">
                <div class="section-title">Totals</div>
                <div class="sub-row">Total Files</div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="totalFilesFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="totalFilesPercent" class="progress-percent">0%</div>
                </div>
                <div id="totalFilesCount" class="progress-count">0 / 0</div>
                <div class="sub-row">Total Lines</div>
                <div class="progress-row">
                    <div class="progress">
                        <div id="totalLinesFill" class="progress-fill" style="width:0%"></div>
                    </div>
                    <div id="totalLinesPercent" class="progress-percent">0%</div>
                </div>
                <div id="totalLinesCount" class="progress-count">0 / 0</div>
            </section>
        </div>
    `;

    // Connect to SSE stream
    const es = new EventSource(`/api/analyze-stream?path=${encodeURIComponent(path)}`);

    // No analysisLog element in the DOM anymore, but we keep the variable reference null
    // so existing logic can check for its existence.
    const logEl = null; 

    // ensure a compact "Recently processed" list exists under the File Explorer
    const fileListContainer = document.getElementById('fileList');
    if (fileListContainer) {
        let streamedContainer = document.getElementById('streamedFilesContainer');
        if (!streamedContainer) {
            streamedContainer = document.createElement('div');
            streamedContainer.id = 'streamedFilesContainer';
            streamedContainer.className = 'streamed-files';
            streamedContainer.innerHTML = '<div class="streamed-header">Recently processed</div><ul id="streamedFiles" class="streamed-file-list"></ul>';
            // insert after fileList so it appears under the File Explorer list
            fileListContainer.parentNode.insertBefore(streamedContainer, fileListContainer.nextSibling);
        }
    }
    const streamedFilesEl = document.getElementById('streamedFiles');

    // Buffers and flush mechanism to prevent the browser from becoming unresponsive
    // when streams produce a very large number of events (thousands of <li>s).
    const FILE_FLUSH_INTERVAL_MS = 150; // flush the buffers every 150ms
    const MAX_LOG_ENTRIES = 2000;       // keep the log bounded to this many entries
    const MAX_BATCH_PER_FLUSH = 200;    // don't append more than this per flush

    let fileBuffer = [];
    let dirBuffer = [];
    let flushTimer = null;

    function scheduleFlush() {
        if (flushTimer) return;
        flushTimer = setInterval(() => {
            // Even if logEl is missing, we must process buffers to update the side list
            
            const frag = document.createDocumentFragment();

            // append up to MAX_BATCH_PER_FLUSH items from directory buffer first
            let appended = 0;
            const addedToStream = [];
            while (dirBuffer.length && appended < MAX_BATCH_PER_FLUSH) {
                const text = dirBuffer.shift();
                if (logEl) {
                    const li = document.createElement('li');
                    li.className = 'analysis-event directory';
                    li.textContent = text;
                    frag.appendChild(li);
                }
                appended++;
            }

            // then append file buffer items up to remaining quota
            while (fileBuffer.length && appended < MAX_BATCH_PER_FLUSH) {
                const text = fileBuffer.shift();
                if (logEl) {
                    const li = document.createElement('li');
                    li.className = 'analysis-event file';
                    li.textContent = text;
                    frag.appendChild(li);
                }
                // also capture file entries for the compact File Explorer list
                addedToStream.push(text);
                appended++;
            }

            if (logEl && frag.childNodes.length) {
                logEl.appendChild(frag);
                // trim log to MAX_LOG_ENTRIES to avoid unbounded growth
                while (logEl.childNodes.length > MAX_LOG_ENTRIES) {
                    logEl.removeChild(logEl.firstChild);
                }
                logEl.scrollTop = logEl.scrollHeight;
            }

            // append a small set of the most recent file paths to the File Explorer side-list
            try {
                if (streamedFilesEl && addedToStream.length) {
                    const frag2 = document.createDocumentFragment();
                    addedToStream.forEach(p => {
                        const li2 = document.createElement('li');
                        li2.className = 'streamed-file';
                        li2.textContent = p;
                        frag2.appendChild(li2);
                    });
                    streamedFilesEl.appendChild(frag2);
                    // keep this list bounded to a reasonable size
                    const MAX_STREAMED_ENTRIES = 80;
                    while (streamedFilesEl.childNodes.length > MAX_STREAMED_ENTRIES) {
                        streamedFilesEl.removeChild(streamedFilesEl.firstChild);
                    }
                }
            } catch (ex) {
                // don't let a side-list error interrupt the main log
                console.debug('Streamed side-list update failed', ex);
            }

            if (dirBuffer.length === 0 && fileBuffer.length === 0) {
                // nothing more to flush — stop the timer until next event
                clearInterval(flushTimer);
                flushTimer = null;
            }
        }, FILE_FLUSH_INTERVAL_MS);
    }

    es.addEventListener('start', (e) => {
        if (logEl) {
            const li = document.createElement('li');
            li.className = 'analysis-event analysis-start';
            li.textContent = e.data;
            logEl.appendChild(li);
            logEl.scrollTop = logEl.scrollHeight;
        }
    });

    es.addEventListener('directory', (e) => {
        // show current folder name (most-recent)
        try { document.getElementById('currentFolderName').textContent = e.data; } catch (err) {}
        // buffer directory entries and flush in batches to avoid UI freezes
        dirBuffer.push(e.data);
        scheduleFlush();
    });

    es.addEventListener('file', (e) => {
        // buffer file entries and flush in batches to avoid UI freezes
        fileBuffer.push(e.data);
        scheduleFlush();
    });

        // current method name events (streamed for languages where we can detect methods)
        es.addEventListener('method', (e) => {
            try {
                const methodName = e.data;
                if (methodName && document.getElementById('currentMethod')) document.getElementById('currentMethod').textContent = methodName;
            } catch (err) {
                // ignore
            }
        });

        es.addEventListener('methodProgress', (e) => {
            try {
                const m = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
                if (m.totalMethods && m.totalMethods > 0) {
                    const pct = Math.round((m.processedMethods * 100)/m.totalMethods);
                    document.getElementById('methodsProgressFill').style.width = pct + '%';
                    document.getElementById('methodsPercent').textContent = pct + '%';
                    document.getElementById('methodsCount').textContent = m.processedMethods + ' / ' + m.totalMethods;
                }
            } catch (err) {
                console.warn('Could not parse methodProgress event', err);
            }
        });
    // initial totals event: contains the total counts so the UI can show percentages
    es.addEventListener('totals', (e) => {
        try {
            const totals = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
            // set up any denominators shown in the UI
            window._analysisTotals = totals;
        } catch (err) {
            console.warn('Could not parse totals event', err);
        }
    });

    // folder progress updates
    es.addEventListener('folderProgress', (e) => {
        try {
            const f = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
            if (f.totalFolders && f.totalFolders > 0) {
                const pct = Math.round((f.processedFolders * 100)/f.totalFolders);
                document.getElementById('folderProgressFill').style.width = pct + '%';
                document.getElementById('folderPercent').textContent = pct + '%';
                document.getElementById('folderCount').textContent = f.processedFolders + ' / ' + f.totalFolders;
            }
        } catch (err) {
            console.warn('Could not parse folderProgress event', err);
        }
    });

    // received per-file statistics (lines, methods, processed counts)
    es.addEventListener('file-stats', (e) => {
        try {
            const stats = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
            if (stats.type === 'code') {
                document.getElementById('currentCodeFile').textContent = stats.path || '(none)';
                // update code file progress
                if (stats.totalCodeFiles && stats.totalCodeFiles > 0) {
                    const pct = Math.round((stats.processedCodeFiles * 100)/stats.totalCodeFiles);
                    document.getElementById('codeFileProgressFill').style.width = pct + '%';
                    document.getElementById('codeFilePercent').textContent = pct + '%';
                    document.getElementById('codeFileCount').textContent = stats.processedCodeFiles + ' / ' + stats.totalCodeFiles;
                }
                // update code lines (prefer percent directly if included)
                if (stats.percentCodeLines !== undefined) {
                    document.getElementById('codeLinesProgressFill').style.width = stats.percentCodeLines + '%';
                    document.getElementById('codeLinesPercent').textContent = stats.percentCodeLines + '%';
                } else if (stats.totalCodeLines && stats.totalCodeLines > 0 && stats.processedCodeLines !== undefined) {
                    const pct2 = Math.round((stats.processedCodeLines * 100)/stats.totalCodeLines);
                    document.getElementById('codeLinesProgressFill').style.width = pct2 + '%';
                    document.getElementById('codeLinesPercent').textContent = pct2 + '%';
                }
                if (stats.totalCodeLines && stats.totalCodeLines > 0 && stats.processedCodeLines !== undefined) {
                     document.getElementById('codeLinesCount').textContent = stats.processedCodeLines + ' / ' + stats.totalCodeLines;
                }
                // methods
                if (stats.totalMethods && stats.totalMethods > 0) {
                    const mp = Math.round((stats.processedMethods * 100)/stats.totalMethods);
                    document.getElementById('methodsProgressFill').style.width = mp + '%';
                    document.getElementById('methodsPercent').textContent = mp + '%';
                    document.getElementById('methodsCount').textContent = stats.processedMethods + ' / ' + stats.totalMethods;
                }
            } else if (stats.type === 'document') {
                // current document/file being processed
                document.getElementById('currentDocFile').textContent = stats.path || '(none)';

                // document file count progress (e.g. processedDocFiles / totalDocFiles)
                if (stats.totalDocFiles && stats.totalDocFiles > 0) {
                    const pct = Math.round((stats.processedDocFiles * 100)/stats.totalDocFiles);
                    document.getElementById('docFileProgressFill').style.width = pct + '%';
                    document.getElementById('docFilePercent').textContent = pct + '%';
                    document.getElementById('docFileCount').textContent = stats.processedDocFiles + ' / ' + stats.totalDocFiles;
                }

                // lines within document files: prefer explicit percent if provided,
                // otherwise compute from processedDocLines / totalDocLines when available
                if (stats.percentDocLines !== undefined) {
                    document.getElementById('docLinesProgressFill').style.width = stats.percentDocLines + '%';
                    document.getElementById('docLinesPercent').textContent = stats.percentDocLines + '%';
                } else if (stats.totalDocLines && stats.totalDocLines > 0 && stats.processedDocLines !== undefined) {
                    const pct2 = Math.round((stats.processedDocLines * 100)/stats.totalDocLines);
                    document.getElementById('docLinesProgressFill').style.width = pct2 + '%';
                    document.getElementById('docLinesPercent').textContent = pct2 + '%';
                }

                // update the numeric processed/total counters (live)
                try {
                    const processed = (stats.processedDocLines !== undefined) ? stats.processedDocLines : null;
                    const total = (stats.totalDocLines !== undefined) ? stats.totalDocLines : (window._analysisTotals ? window._analysisTotals.totalDocLines : null);
                    if (processed !== null && total !== null) {
                        document.getElementById('docLinesCount').textContent = processed + ' / ' + total;
                    } else if (total !== null) {
                        // show '0 / <total>' while processing begins
                        document.getElementById('docLinesCount').textContent = '0 / ' + total;
                    }
                } catch (err) { /* ignore missing node */ }
            }
        } catch (err) {
            console.warn('Could not parse file-stats event', err);
        }
    });

    // progress brings a compact overall summary so update totals and global bars
    es.addEventListener('progress', (e) => {
        try {
            const p = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
            if (p.totalFiles && p.totalFiles > 0) {
                const tf = Math.round((p.processedFiles * 100)/p.totalFiles);
                document.getElementById('totalFilesFill').style.width = tf + '%';
                document.getElementById('totalFilesPercent').textContent = tf + '%';
                document.getElementById('totalFilesCount').textContent = p.processedFiles + ' / ' + p.totalFiles;
            }
            if (p.totalLines && p.totalLines > 0) {
                const tl = Math.round((p.processedLines * 100)/p.totalLines);
                document.getElementById('totalLinesFill').style.width = tl + '%';
                document.getElementById('totalLinesPercent').textContent = tl + '%';
                document.getElementById('totalLinesCount').textContent = p.processedLines + ' / ' + p.totalLines;
            }
            // update code lines if included
            if (p.totalCodeLines && p.totalCodeLines > 0) {
                const cl = Math.round((p.processedCodeLines * 100)/p.totalCodeLines);
                document.getElementById('codeLinesProgressFill').style.width = cl + '%';
                document.getElementById('codeLinesPercent').textContent = cl + '%';
                document.getElementById('codeLinesCount').textContent = p.processedCodeLines + ' / ' + p.totalCodeLines;
            }

            // update doc files progress if included
            if (p.totalDocFiles && p.totalDocFiles > 0) {
                const df = Math.round((p.processedDocFiles * 100)/p.totalDocFiles);
                document.getElementById('docFileProgressFill').style.width = df + '%';
                document.getElementById('docFilePercent').textContent = df + '%';
                document.getElementById('docFileCount').textContent = p.processedDocFiles + ' / ' + p.totalDocFiles;
            }

            // update document lines progress if included
            if (p.totalDocLines && p.totalDocLines > 0) {
                const dl = Math.round((p.processedDocLines * 100)/p.totalDocLines);
                document.getElementById('docLinesProgressFill').style.width = dl + '%';
                document.getElementById('docLinesPercent').textContent = dl + '%';
                // update the compact counter (processed / total) if available in the compact progress event
                try {
                    const processed = p.processedDocLines !== undefined ? p.processedDocLines : null;
                    if (processed !== null) {
                        document.getElementById('docLinesCount').textContent = processed + ' / ' + p.totalDocLines;
                    } else {
                        // fallback to '0 / total'
                        document.getElementById('docLinesCount').textContent = '0 / ' + p.totalDocLines;
                    }
                } catch (err) { /* ignore DOM issues */ }
            }
        } catch (err) {
            console.warn('Could not parse progress event', err);
        }
    });

    // es.addEventListener('result', (e) => {
    //     // final result is a JSON object
    //     try {
    //         const result = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
    //         // displayAnalysisResults(result); // User requested to keep the streaming view
    //         console.log('Analysis complete (result received)', result);
    //     } catch (err) {
    //         console.warn('Could not parse result event', err);
    //     }
    //     // keep the connection open — wait for the 'saved' or 'done' event before closing
    // });

    // saved event includes resultsPath and a compact summary — show link/info and close connection
    es.addEventListener('saved', (e) => {
        try {
            const info = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
            
            // 1. Move files to side list (File Explorer side)
            // This preserves the "Recently processed" list even after the main view is cleared.
            try {
                if (info.savedFiles && info.savedFiles.length && streamedFilesEl) {
                    const frag2 = document.createDocumentFragment();
                    info.savedFiles.forEach(p => {
                        const li2 = document.createElement('li');
                        li2.className = 'streamed-file';
                        li2.textContent = p;
                        frag2.appendChild(li2);
                    });
                    streamedFilesEl.appendChild(frag2);
                    const MAX_STREAMED_ENTRIES = 80;
                    while (streamedFilesEl.childNodes.length > MAX_STREAMED_ENTRIES) {
                        streamedFilesEl.removeChild(streamedFilesEl.firstChild);
                    }
                }
            } catch (ex) { console.debug('Failed to add saved files to side-list', ex); }

            // 2. Keep the streaming progress view (percent complete) but clear the log
            // This satisfies "Keep percent complete" while respecting "Move" (remove files from results).
            const resultsDiv = document.getElementById('analysisResults');
            const logEl = document.getElementById('analysisLog');
            if (logEl) logEl.innerHTML = '';

            // 3. Log saved path (no UI badge)
            if (resultsDiv) {
                const rp = info.resultsPath || e.data;
                console.debug('Analysis results saved to:', rp);
            }

            // 4. Clear buffers and stop timer
            fileBuffer = [];
            dirBuffer = [];
            if (flushTimer) {
                clearInterval(flushTimer);
                flushTimer = null;
            }

        } catch (err) {
            console.warn('Could not parse saved event', err);
        }
        es.close();
    });

    es.addEventListener('error', (e) => {
        if (e && e.data) {
            resultsDiv.innerHTML = `<div class="error-message">Error: ${e.data}</div>`;
        } else {
            // If EventSource encounters an error it may send a generic event; leave existing results
        }
        // make sure we flush any remaining items and stop the periodic flush
        if (flushTimer) {
            clearInterval(flushTimer);
            flushTimer = null;
        }
        // try a final flush (best-effort)
        if (logEl && (dirBuffer.length || fileBuffer.length)) {
            const frag = document.createDocumentFragment();
            while (dirBuffer.length) {
                const text = dirBuffer.shift();
                const li = document.createElement('li');
                li.className = 'analysis-event directory';
                li.textContent = text;
                frag.appendChild(li);
            }
            while (fileBuffer.length) {
                const text = fileBuffer.shift();
                const li = document.createElement('li');
                li.className = 'analysis-event file';
                li.textContent = text;
                frag.appendChild(li);
            }
            logEl.appendChild(frag);
            while (logEl.childNodes.length > MAX_LOG_ENTRIES) {
                logEl.removeChild(logEl.firstChild);
            }
            logEl.scrollTop = logEl.scrollHeight;
        }
        es.close();
    });

    // done event (controller-level completion) - ensure connection closed
    es.addEventListener('done', (e) => {
        // ensure buffered entries are flushed and stop any timers before closing
        if (flushTimer) {
            clearInterval(flushTimer);
            flushTimer = null;
        }
        if (logEl && (dirBuffer.length || fileBuffer.length)) {
            const frag = document.createDocumentFragment();
            while (dirBuffer.length) {
                const text = dirBuffer.shift();
                const li = document.createElement('li');
                li.className = 'analysis-event directory';
                li.textContent = text;
                frag.appendChild(li);
            }
            while (fileBuffer.length) {
                const text = fileBuffer.shift();
                const li = document.createElement('li');
                li.className = 'analysis-event file';
                li.textContent = text;
                frag.appendChild(li);
            }
            logEl.appendChild(frag);
            while (logEl.childNodes.length > MAX_LOG_ENTRIES) {
                logEl.removeChild(logEl.firstChild);
            }
            logEl.scrollTop = logEl.scrollHeight;
        }
        try { es.close(); } catch (ex) { /* ignore */ }
    });
}

function displayAnalysisResults(results) {
    const resultsDiv = document.getElementById('analysisResults');

    const html = `
        <div class="stat-grid">
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalFolders ?? 0)}</div>
                <div class="stat-label">Folders</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalFiles ?? 0)}</div>
                <div class="stat-label">Total Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalCodeFiles ?? 0)}</div>
                <div class="stat-label">Code Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalDocFiles ?? 0)}</div>
                <div class="stat-label">Doc Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalOtherFiles ?? 0)}</div>
                <div class="stat-label">Other Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalMethods ?? 0)}</div>
                <div class="stat-label">Total Methods</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalCodeLines ?? 0)}</div>
                <div class="stat-label">Total Code Lines</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalDocLines ?? 0)}</div>
                <div class="stat-label">Total Doc Lines</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalLines ?? 0)}</div>
                <div class="stat-label">Total Lines</div>
            </div>
        </div>
    `;

    resultsDiv.innerHTML = html;
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function formatNumber(num) {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

function capitalizeFirst(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
