
let currentPath = '';
let historyStack = [];
let forwardStack = [];

document.addEventListener('DOMContentLoaded', function() {
    const pathInput = document.getElementById('currentPath');
    const analyzeBtn = document.getElementById('analyzeBtn');
    const backBtn = document.getElementById('backBtn');

    // Set default path to user home or common location
    if (navigator.platform.toLowerCase().includes('win')) {
        pathInput.value = 'C:\\';
    } else {
        pathInput.value = '/home';
    }
    currentPath = pathInput.value;

    // Navigation can be triggered by pressing Enter in the input or by clicking folder items

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
    resultsDiv.innerHTML = '<div class="loading">Analyzing directory</div>';

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
        <div class="loading">Analyzing directory</div>
        <div id="analysisLive" class="analysis-live">
            <div class="analysis-live-header">Processing files and folders</div>
            <ul id="analysisLog" class="analysis-log"></ul>
        </div>
    `;

    // Connect to SSE stream
    const es = new EventSource(`/api/analyze-stream?path=${encodeURIComponent(path)}`);

    const logEl = document.getElementById('analysisLog');

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
        if (logEl) {
            const li = document.createElement('li');
            li.className = 'analysis-event directory';
            li.textContent = e.data;
            logEl.appendChild(li);
            logEl.scrollTop = logEl.scrollHeight;
        }
    });

    es.addEventListener('file', (e) => {
        if (logEl) {
            const li = document.createElement('li');
            li.className = 'analysis-event file';
            li.textContent = e.data;
            logEl.appendChild(li);
            // keep it scroll to bottom
            logEl.scrollTop = logEl.scrollHeight;
        }
    });

    es.addEventListener('result', (e) => {
        // final result is a JSON object
        try {
            const result = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
            displayAnalysisResults(result);
        } catch (err) {
            console.warn('Could not parse result event', err);
        }
        es.close();
    });

    es.addEventListener('error', (e) => {
        if (e && e.data) {
            resultsDiv.innerHTML = `<div class="error-message">Error: ${e.data}</div>`;
        } else {
            // If EventSource encounters an error it may send a generic event; leave existing results
        }
        es.close();
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
                <div class="stat-label">Documents</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalMethods ?? 0)}</div>
                <div class="stat-label">Methods</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalLines ?? 0)}</div>
                <div class="stat-label">Lines of Code</div>
            </div>
        </div>
        <div class="analysis-path">
            <strong>Analyzed Path:</strong><br>
            ${escapeHtml(results.path)}
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
