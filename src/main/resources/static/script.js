const API_BASE = "";

let documents = [];
let selectedDocId = null;

const fileInput = document.getElementById("fileInput");
const dropzone = document.getElementById("dropzone");
const dropzoneTitle = document.getElementById("dropzoneTitle");
const uploadBtn = document.getElementById("uploadBtn");
const uploadStatus = document.getElementById("uploadStatus");
const docBlock = document.getElementById("docBlock");
const docList = document.getElementById("docList");
const askBlock = document.getElementById("askBlock");
const selectedDocLabel = document.getElementById("selectedDocLabel");
const questionInput = document.getElementById("questionInput");
const askBtn = document.getElementById("askBtn");
const scanLine = document.getElementById("scanLine");
const answerBox = document.getElementById("answerBox");
const answerText = document.getElementById("answerText");
const sourcesList = document.getElementById("sourcesList");

fileInput.addEventListener("change", () => {
    const file = fileInput.files[0];
    if (file) {
        dropzoneTitle.textContent = file.name;
        uploadBtn.disabled = false;
    }
});

uploadBtn.addEventListener("click", async () => {
    const file = fileInput.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    uploadBtn.disabled = true;
    setStatus("Reading and chunking the document, this can take a moment…", null);

    try {
        const response = await fetch(`${API_BASE}/api/documents/upload`, {
            method: "POST",
            body: formData
        });

        if (!response.ok) {
            const errorText = await response.text();
            setStatus(errorText, "error");
            uploadBtn.disabled = false;
            return;
        }

        const doc = await response.json();
        documents.push(doc);
        renderDocList();
        docBlock.style.display = "block";
        setStatus(`Done — "${doc.fileName}" split into ${doc.chunks.length} passages.`, "success");
        fileInput.value = "";
        dropzoneTitle.textContent = "Choose a PDF, or drop one here";
    } catch (err) {
        setStatus(`Network error: ${err.message}`, "error");
    } finally {
        uploadBtn.disabled = true;
    }
});

function setStatus(message, type) {
    uploadStatus.textContent = message;
    uploadStatus.className = "status-line" + (type ? " " + type : "");
}

function renderDocList() {
    docList.innerHTML = "";
    documents.forEach(doc => {
        const item = document.createElement("div");
        item.className = "doc-item" + (doc.id === selectedDocId ? " selected" : "");
        item.innerHTML = `
            <span class="doc-name">${doc.fileName}</span>
            <span class="doc-meta">${doc.chunks.length} passages</span>
        `;
        item.addEventListener("click", () => selectDocument(doc));
        docList.appendChild(item);
    });
}

function selectDocument(doc) {
    selectedDocId = doc.id;
    renderDocList();
    askBlock.style.display = "block";
    selectedDocLabel.innerHTML = `Asking about <strong>${doc.fileName}</strong>`;
    answerBox.style.display = "none";
    questionInput.focus();
}

askBtn.addEventListener("click", askQuestion);
questionInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") askQuestion();
});

async function askQuestion() {
    const question = questionInput.value.trim();
    if (!question || !selectedDocId) return;

    askBtn.disabled = true;
    scanLine.classList.add("active");
    answerBox.style.display = "none";

    try {
        const response = await fetch(`${API_BASE}/api/documents/ask`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ documentId: selectedDocId, question })
        });

        const data = await response.json();

        if (!response.ok) {
            answerText.textContent = typeof data === "string" ? data : "Something went wrong.";
            sourcesList.innerHTML = "";
            answerBox.style.display = "block";
            return;
        }

        answerText.textContent = data.answer;
        sourcesList.innerHTML = "";

        (data.sourceChunks || []).forEach(source => {
            const div = document.createElement("div");
            div.className = "source-chunk";
            const pageLabel = source.pageNumber ? `Page ${source.pageNumber}` : "Page unknown";
            div.innerHTML = `<span class="page-tag">${pageLabel}</span><div>${source.content}</div>`;
            sourcesList.appendChild(div);
        });

        answerBox.style.display = "block";
    } catch (err) {
        answerText.textContent = `Network error: ${err.message}`;
        sourcesList.innerHTML = "";
        answerBox.style.display = "block";
    } finally {
        askBtn.disabled = false;
        scanLine.classList.remove("active");
    }
}