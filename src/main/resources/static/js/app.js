let currentPath = '';

document.addEventListener('DOMContentLoaded', function() {
    const pathInput = document.getElementById('currentPath');
    const navigateBtn = document.getElementById('navigateBtn');
    const analyzeBtn = document.getElementById('analyzeBtn');

    // Set default path to user home or common location
    if (navigator.platform.toLowerCase().includes('win')) {
        pathInput.value = 'C:\\';
    } else {
        pathInput.value = '/home';
    }
    currentPath = pathInput.value;

    navigateBtn.addEventListener('click', () => {
        currentPath = pathInput.value;
        loadDirectory(currentPath);
    });

    analyzeBtn.addEventListener('click', () => {
        currentPath = pathInput.value;
        analyzeDirectory(currentPath);
    });

    pathInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            currentPath = pathInput.value;
            loadDirectory(currentPath);
        }
    });

    // Load initial directory
    loadDirectory(currentPath);
});

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
            <div class="file-item" onclick="navigateToParent('${path}')">
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
        const clickHandler = file.directory ? `navigateToDirectory('${file.path}')` : '';

        html += `
            <div class="file-item" ${clickHandler ? `onclick="${clickHandler}"` : ''}>
                <span class="file-icon">${icon}</span>
                <div class="file-info">
                    <div class="file-name">${escapeHtml(file.name)}</div>
                    <div class="file-type">${typeLabel}${!file.directory && file.size ? ` - ${formatBytes(file.size)}` : ''}</div>
                </div>
            </div>
        `;
    });

    fileList.innerHTML = html;
}

function getFileIcon(type) {
    switch(type) {
        case 'code': return '💻';
        case 'document': return '📄';
        default: return '📄';
    }
}

function navigateToDirectory(path) {
    currentPath = path;
    document.getElementById('currentPath').value = path;
    loadDirectory(path);
}

function navigateToParent(path) {
    const separator = path.includes('\\') ? '\\' : '/';
    const parts = path.split(separator).filter(p => p);
    parts.pop();
    
    let parentPath;
    if (parts.length === 0) {
        parentPath = separator;
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

function displayAnalysisResults(results) {
    const resultsDiv = document.getElementById('analysisResults');

    const html = `
        <div class="stat-grid">
            <div class="stat-card">
                <div class="stat-value">${results.totalFolders}</div>
                <div class="stat-label">Folders</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.totalFiles}</div>
                <div class="stat-label">Total Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.totalCodeFiles}</div>
                <div class="stat-label">Code Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.totalDocFiles}</div>
                <div class="stat-label">Documents</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.totalMethods}</div>
                <div class="stat-label">Methods</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${formatNumber(results.totalLines)}</div>
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
