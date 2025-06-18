chrome.runtime.onMessage.addListener(async (request, sender, sendResponse) => {
  const { youtubeUrl, question } = request;

  try {
    // Send data to your Java server's transcription and OpenAI API handling endpoint
    const response = await fetch('http://localhost:8080/api/process', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ youtubeUrl, question })
    });

    const result = await response.json();
    // Return the OpenAI answer to the popup.js
    sendResponse({ answer: result.answer });
  } catch (error) {
    console.error('Error fetching from server:', error);
    sendResponse({ answer: 'Failed to get an answer.' });
  }

  return true; // Indicates asynchronous response
});
