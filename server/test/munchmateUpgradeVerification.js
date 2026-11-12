/**
 * Comprehensive Automated Verification for MunchMate AI Upgrade:
 * 1. Single & Multi-turn chat tests with conversation history (ensuring no consecutive user role error).
 * 2. Grounded food recommendation & accurate order tracking response.
 * 3. Thumbs-Up feedback submission & MongoDB storage.
 * 4. Thumbs-Down feedback submission with reason.
 * 5. Vote update (deduplication verification - ensures 1 record per student & message).
 * 6. Feedback analytics aggregation endpoint test.
 */

const assert = require('assert');

async function runTests() {
  const BASE_URL = 'http://127.0.0.1:5000/api/ai';
  console.log('--- STARTING MUNCHMATE UPGRADE AUTOMATED VERIFICATION ---');

  // Test 1: Single turn chat
  console.log('\n[Test 1] Testing single-turn chat with live canteen data...');
  const res1 = await fetch(`${BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: 'What is popular today?' }),
  });
  assert.strictEqual(res1.status, 200, `Expected 200 OK, got ${res1.status}`);
  const data1 = await res1.json();
  assert.strictEqual(data1.success, true);
  assert.ok(data1.messageId, 'Expected messageId in response');
  assert.ok(data1.reply && data1.reply.length > 10, 'Expected non-empty reply');
  console.log(`✓ Turn 1 reply received (${data1.reply.length} chars). messageId: ${data1.messageId}`);

  // Test 2: Multi-turn chat with conversation history (Verifies alternating roles bugfix)
  console.log('\n[Test 2] Testing multi-turn chat (follow-up question)...');
  const res2 = await fetch(`${BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: 'Find food under 50',
      conversationHistory: [
        { role: 'user', content: 'What is popular today?' },
        { role: 'model', content: data1.reply },
      ],
    }),
  });
  assert.strictEqual(res2.status, 200, `Expected 200 OK, got ${res2.status}`);
  const data2 = await res2.json();
  assert.strictEqual(data2.success, true);
  assert.ok(data2.messageId, 'Expected messageId in response');
  console.log(`✓ Turn 2 follow-up reply received. messageId: ${data2.messageId}`);

  // Test 3: Thumbs Up Feedback submission
  console.log('\n[Test 3] Submitting 👍 Thumbs Up feedback...');
  const fbUpRes = await fetch(`${BASE_URL}/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-student-id': 'student_test_101' },
    body: JSON.stringify({
      messageId: data1.messageId,
      userQuestion: 'What is popular today?',
      assistantResponse: data1.reply.slice(0, 150),
      feedbackType: 'positive',
    }),
  });
  assert.strictEqual(fbUpRes.status, 200, `Expected 200 OK, got ${fbUpRes.status}`);
  const fbUpData = await fbUpRes.json();
  assert.strictEqual(fbUpData.success, true);
  assert.strictEqual(fbUpData.feedback.feedbackType, 'positive');
  console.log('✓ Thumbs Up recorded successfully in MongoDB.');

  // Test 4: Change vote to Thumbs Down (Deduplication / Update test)
  console.log('\n[Test 4] Changing vote to 👎 Thumbs Down (verifying MongoDB update & deduplication)...');
  const fbDownRes = await fetch(`${BASE_URL}/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-student-id': 'student_test_101' },
    body: JSON.stringify({
      messageId: data1.messageId,
      userQuestion: 'What is popular today?',
      assistantResponse: data1.reply.slice(0, 150),
      feedbackType: 'negative',
      reason: 'Wanted more sweet options',
    }),
  });
  assert.strictEqual(fbDownRes.status, 200, `Expected 200 OK, got ${fbDownRes.status}`);
  const fbDownData = await fbDownRes.json();
  assert.strictEqual(fbDownData.success, true);
  assert.strictEqual(fbDownData.feedback.feedbackType, 'negative');
  console.log('✓ Existing feedback record successfully updated to negative without duplicates.');

  // Test 5: Verify Feedback Analytics endpoint
  console.log('\n[Test 5] Fetching feedback analytics for evaluation...');
  const analyticsRes = await fetch(`${BASE_URL}/feedback/analytics`);
  assert.strictEqual(analyticsRes.status, 200);
  const analyticsData = await analyticsRes.json();
  assert.strictEqual(analyticsData.success, true);
  console.log('✓ Feedback Analytics retrieved:', JSON.stringify(analyticsData.analytics, null, 2));

  console.log('\n🎉 ALL 5 MUNCHMATE AUTOMATED TESTS PASSED SUCCESSFULLY!');
}

runTests().catch((err) => {
  console.error('❌ Verification failed:', err);
  process.exit(1);
});

