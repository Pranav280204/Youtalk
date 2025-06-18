document.getElementById('askButton').addEventListener('click', async function () {
    const youtubeUrl = document.getElementById('youtubeUrl').value;
    const question = document.getElementById('question').value;
    const answerBox = document.getElementById('answerText');
    answerBox.textContent = 'Processing... Please wait.';

    const youtubeRegex = /^(https?:\/\/)?(www\.)?(youtube\.com|youtu\.be)\/.+$/;
    if (!youtubeRegex.test(youtubeUrl)) {
        answerBox.textContent = 'Invalid YouTube URL.';
        return;
    }

    if (youtubeUrl && question) {
        try {
            const response = await fetch('http://localhost:8080/api/process', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: new URLSearchParams({
                    url: youtubeUrl,
                    question: question
                })
            });

            if (response.ok) {
                const answer = await response.text();
                answerBox.textContent = answer.replace(/[\r\n]+/g, ' ').trim();
            } else {
                answerBox.textContent = `Error: ${response.status} - ${response.statusText}`;
            }
        } catch (error) {
            answerBox.textContent = `Error: ${error.message}`;
        }
    } else {
        answerBox.textContent = 'Please provide both the YouTube URL and a question.';
    }
});

// New event listener for the "Get Summary" button
document.getElementById('summaryButton').addEventListener('click', async function () {
    const youtubeUrl = document.getElementById('youtubeUrl').value;
    const answerBox = document.getElementById('answerText');
    answerBox.textContent = 'Fetching summary... Please wait.';

    const youtubeRegex = /^(https?:\/\/)?(www\.)?(youtube\.com|youtu\.be)\/.+$/;
    if (!youtubeRegex.test(youtubeUrl)) {
        answerBox.textContent = 'Invalid YouTube URL.';
        return;
    }

    if (youtubeUrl) {
        try {
            const response = await fetch('http://localhost:8080/api/getSummary', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: new URLSearchParams({
                    url: youtubeUrl
                })
            });

            if (response.ok) {
                const summary = await response.text();
                answerBox.textContent = summary.replace(/[\r\n]+/g, ' ').trim();
            } else {
                answerBox.textContent = `Error: ${response.status} - ${response.statusText}`;
            }
        } catch (error) {
            answerBox.textContent = `Error: ${error.message}`;
        }
    } else {
        answerBox.textContent = 'Please provide a valid YouTube URL.';
    }
});
