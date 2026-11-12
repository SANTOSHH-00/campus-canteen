const path = require('path');
const dotenv = require('dotenv');
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const connectDB = require('../src/config/db');
const { handleChatStream } = require('../src/controllers/aiController');

async function testPopular() {
  await connectDB();
  const req = {
    body: {
      message: "What's popular today?",
      studentId: 'test_student_123',
    },
    headers: {},
  };

  const startTime = Date.now();
  let firstChunkTime = null;

  const res = {
    headers: {},
    setHeader(name, val) { this.headers[name] = val; },
    flushHeaders() {},
    write(data) {
      const now = Date.now();
      if (!firstChunkTime) firstChunkTime = now - startTime;
      console.log(`[Chunk +${now - startTime}ms]:`, data.slice(0, 120));
    },
    end() {
      const totalTime = Date.now() - startTime;
      console.log(`\nDONE: ${totalTime}ms | First chunk: ${firstChunkTime}ms`);
      process.exit(0);
    },
    status() { return this; },
    json() { process.exit(1); },
  };

  await handleChatStream(req, res);
}

testPopular().catch(console.error);
