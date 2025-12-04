let currentPath = '';

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    loadRoots();
    setupEventListeners();
});

function setupEventListeners() {
    document.getElementById('goButton').addEventListener('click', () => {
        const path = document.getElementById('pathInput').value;
        if (path) {
            loadDirectory(path);
        }
    });

    document.getElementById('homeButton').addEventListener('click', () => {
        loadRoots();
    });

    document.getElementById('pathInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            const path = document.getElementById('pathInput').value;
            if (path) {
                loadDirectory(path);
            }
        }
    });

    document.getElementById('analyzeButton').addEventListener('click', () => {
        if (currentPath) {
            analyzeDirectory(currentPath);
        }
    });
}

async function loadRoots() {
    try {
        const response = await fetch('/api/roots');
        const data = await response.json();
        
        if (data.home) {
            loadDirectory(data.home);
        }
    } catch (error) {
        showError('Failed to load roots: ' + error.message);
    }
}

async function loadDirectory(path) {
    const explorer = document.getElementById('fileExplorer');
    explorer.innerHTML = '<div class="loading">Loading...</div>';
    
    try {
        const response = await fetch(`/api/list?path=${encodeURIComponent(path)}`);
        const data = await response.json();
        
        if (data.error) {
            showError(data.error);
            return;
        }
        
        currentPath = data.path;
        document.getElementById('currentPath').textContent = currentPath;
        document.getElementById('pathInput').value = currentPath;
        
        displayFiles(data.files);
    } catch (error) {
        showError('Failed to load directory: ' + error.message);
    }
}

function displayFiles(files) {
    const explorer = document.getElementById('fileExplorer');
    explorer.innerHTML = '';
    
    if (files.length === 0) {
        explorer.innerHTML = '<div class="loading">Empty directory</div>';
        return;
    }
    
    files.forEach(file => {
        const fileItem = createFileItem(file);
        explorer.appendChild(fileItem);
    });
}

function createFileItem(file) {
    const div = document.createElement('div');
    div.className = `file-item ${file.directory ? 'directory' : file.type}`;
    
    const icon = document.createElement('span');
    icon.className = 'file-icon';
    icon.textContent = getFileIcon(file);
    
    const name = document.createElement('span');
    name.className = 'file-name';
    name.textContent = file.name;
    
    div.appendChild(icon);
    div.appendChild(name);
    
    if (!file.directory && file.size) {
        const size = document.createElement('span');
        size.className = 'file-size';
        size.textContent = formatFileSize(file.size);
        div.appendChild(size);
    }
    
    if (file.directory) {
        div.addEventListener('click', () => {
            loadDirectory(file.path);
        });
    }
    
    return div;
}

function getFileIcon(file) {
    if (file.directory) {
        return '📁';
    }
    
    switch (file.type) {
        case 'code':
            return '💻';
        case 'document':
            return '📄';
        default:
            return '📄';
    }
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

async function analyzeDirectory(path) {
    const resultsDiv = document.getElementById('analysisResults');
    resultsDiv.innerHTML = '<div class="analyzing"><div class="spinner"></div><p>Analyzing directory...</p></div>';
    
    try {
        const response = await fetch(`/api/analyze?path=${encodeURIComponent(path)}`);
        const data = await response.json();
        
        if (data.error) {
            showError(data.error);
            return;
        }
        
        displayAnalysisResults(data);
    } catch (error) {
        showError('Failed to analyze directory: ' + error.message);
    }
}

function displayAnalysisResults(results) {
    const resultsDiv = document.getElementById('analysisResults');
    
    resultsDiv.innerHTML = `
        <div class="stat-grid">
            <div class="stat-card highlight">
                <div class="stat-value">${results.totalFolders}</div>
                <div class="stat-label">Folders</div>
            </div>
            <div class="stat-card highlight">
                <div class="stat-value">${results.totalFiles}</div>
                <div class="stat-label">Total Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.codeFiles}</div>
                <div class="stat-label">Code Files</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.documentFiles}</div>
                <div class="stat-label">Documents</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.totalMethods}</div>
                <div class="stat-label">Methods</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${results.totalLines}</div>
                <div class="stat-label">Lines of Code</div>
            </div>
        </div>
        <div style="margin-top: 20px; padding: 15px; background: #e7f3ff; border-radius: 6px;">
            <strong>Path:</strong> ${results.path}
        </div>
    `;
}

function showError(message) {
    const explorer = document.getElementById('fileExplorer');
    explorer.innerHTML = `<div class="error">⚠️ ${message}</div>`;
}
