const http = require('http');

async function testStream() {
  const postData = JSON.stringify({
    message: 'Track my latest order',
    studentId: 'test_student_123',
  });

  const options = {
    hostname: '127.0.0.1',
    port: 5000,
    path: '/api/ai/stream',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(postData),
    },
  };

  const startTime = Date.now();
  console.log('Sending streaming request to http://127.0.0.1:5000/api/ai/stream ...');

  const req = http.request(options, (res) => {
    console.log(`HTTP Status: ${res.statusCode}`);
    console.log(`Content-Type: ${res.headers['content-type']}`);

    let firstChunkTime = null;
    let totalChunks = 0;

    res.on('data', (chunk) => {
      const now = Date.now();
      if (!firstChunkTime) {
        firstChunkTime = now - startTime;
        console.log(`>>> FIRST CHUNK RECEIVED IN ${firstChunkTime} ms! <<<`);
      }
      totalChunks++;
      const text = chunk.toString();
      console.log(`[Chunk #${totalChunks} +${now - startTime}ms]:`, text.replace(/\n+/g, ' '));
    });

    res.on('end', () => {
      const totalTime = Date.now() - startTime;
      console.log('--- STREAM FINISHED ---');
      console.log(`Total time: ${totalTime} ms (${(totalTime / 1000).toFixed(2)}s)`);
      console.log(`Total chunks: ${totalChunks}`);
    });
  });

  req.on('error', (e) => {
    console.error('Request error:', e.message);
  });

  req.write(postData);
  req.end();
}

testStream();
