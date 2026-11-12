const path = require('path');
const dotenv = require('dotenv');
dotenv.config({ path: path.resolve(__dirname, '../../.env') });
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const connectDB = require('../src/config/db');
const { handleChatStream } = require('../src/controllers/aiController');

async function runDirectStreamTest() {
  await connectDB();
  console.log('MongoDB connected for stream test.');

  const req = {
    body: {
      message: 'Track my latest order',
      studentId: 'test_student_123',
    },
    headers: {},
  };

  const startTime = Date.now();
  let firstChunkTime = null;
  let chunkCount = 0;

  const res = {
    headers: {},
    setHeader(name, val) {
      this.headers[name] = val;
    },
    flushHeaders() {},
    write(data) {
      const now = Date.now();
      if (!firstChunkTime) {
        firstChunkTime = now - startTime;
        console.log(`\n⚡ >>> FIRST CHUNK EMITTED IN ${firstChunkTime} ms! <<< ⚡\n`);
      }
      chunkCount++;
      console.log(`[Event #${chunkCount} +${now - startTime}ms]:`, data.replace(/\n+/g, ' '));
    },
    end() {
      const totalTime = Date.now() - startTime;
      console.log('\n========================================');
      console.log('STREAM COMPLETED SUCCESSFULLY');
      console.log(`Total Elapsed Time: ${totalTime} ms (${(totalTime / 1000).toFixed(2)}s)`);
      console.log(`Target: ~5.00s (PASSED: ${totalTime <= 7000 ? 'YES' : 'NO'})`);
      console.log(`First visible chunk: ${firstChunkTime} ms (Target 1000-2000ms)`);
      console.log(`Total events emitted: ${chunkCount}`);
      console.log('========================================\n');
      process.exit(0);
    },
    status(code) {
      console.log('Status set to:', code);
      return this;
    },
    json(obj) {
      console.log('JSON returned:', obj);
      process.exit(1);
    },
  };

  console.log('Invoking handleChatStream with: "Track my latest order"...');
  await handleChatStream(req, res);
}

runDirectStreamTest().catch(err => {
  console.error('Test error:', err);
  process.exit(1);
});
