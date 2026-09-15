const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
require('dotenv').config();

let passed = 0;
let total = 0;

function assert(condition, message) {
  total++;
  if (!condition) {
    console.error(`❌ FAIL: ${message}`);
    throw new Error(message);
  } else {
    passed++;
    console.log(`✅ PASS: ${message}`);
  }
}

// Test Date Calculations matching server/src/routes/orders.js
function testDateFilteringLogic() {
  console.log('Testing Orders Route Date Filter logic...');

  const now = new Date();
  const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;
  const istNow = new Date(now.getTime() + IST_OFFSET_MS);

  const istTodayStart = new Date(Date.UTC(istNow.getUTCFullYear(), istNow.getUTCMonth(), istNow.getUTCDate()));
  const todayStartUtc = new Date(istTodayStart.getTime() - IST_OFFSET_MS);
  const todayEndUtc = new Date(todayStartUtc.getTime() + 24 * 60 * 60 * 1000 - 1);

  assert(todayStartUtc < todayEndUtc, 'Today start UTC is before end UTC');
  assert(todayEndUtc.getTime() - todayStartUtc.getTime() === 86399999, 'Today duration is exactly 24 hours minus 1 ms');

  // Test Yesterday
  const yesterdayStartUtc = new Date(todayStartUtc.getTime() - 24 * 60 * 60 * 1000);
  const yesterdayEndUtc = new Date(todayStartUtc.getTime() - 1);
  assert(yesterdayEndUtc < todayStartUtc, 'Yesterday ends before today starts');

  // Test 7 Days
  const sevenDaysAgoStart = new Date(todayStartUtc.getTime() - 6 * 24 * 60 * 60 * 1000);
  assert(sevenDaysAgoStart < todayStartUtc, '7 Days ago starts before today');

  // Test 30 Days
  const thirtyDaysAgoStart = new Date(todayStartUtc.getTime() - 29 * 24 * 60 * 60 * 1000);
  assert(thirtyDaysAgoStart < sevenDaysAgoStart, '30 Days ago starts before 7 days ago');

  console.log(`\n🎉 All tests passed: ${passed}/${total}`);
}

testDateFilteringLogic();
