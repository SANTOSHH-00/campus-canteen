const assert = require('assert');
const { generateItemDescription } = require('../src/services/geminiService');
const Item = require('../src/models/Item');
const Review = require('../src/models/Review');

async function runTests() {
  console.log('--- Starting Food Item Redesign Unit Tests ---');

  // Test 1: Gemini description generation with verified ingredients
  console.log('Test 1: Testing generateItemDescription with ingredients...');
  const res1 = await generateItemDescription({
    name: 'Chana Bhatura',
    category: 'BREAKFAST',
    ingredients: ['Chickpeas', 'Fluffy Bhatura bread', 'Spices', 'Pickle'],
  });
  console.log('Result 1:', res1);
  assert(res1 && typeof res1.description === 'string', 'Description should be a string');
  assert(res1.description.length > 10, 'Description should be descriptive');
  console.log('✅ Test 1 Passed: Generated valid description draft');

  // Test 2: Gemini description generation without ingredients
  console.log('\nTest 2: Testing generateItemDescription without ingredients...');
  const res2 = await generateItemDescription({
    name: 'Special Thali',
    category: 'LUNCH',
    ingredients: [],
  });
  console.log('Result 2:', res2);
  assert(res2 && typeof res2.description === 'string', 'Description should be a string');
  assert(res2.description.includes('Special Thali') || res2.description.length > 10, 'Description should mention dish or be descriptive');
  console.log('✅ Test 2 Passed: Generated fallback description draft gracefully');

  // Test 3: Review model validation
  console.log('\nTest 3: Testing Review model schema definition...');
  assert(Review.schema.paths.itemId, 'Review should have itemId path');
  assert(Review.schema.paths.rating, 'Review should have rating path');
  assert(Review.schema.paths.comment, 'Review should have comment path');
  assert(Review.schema.paths.userName, 'Review should have userName path');
  console.log('✅ Test 3 Passed: Review schema definition verified');

  // Test 4: Item model reviewCount
  console.log('\nTest 4: Testing Item schema reviewCount path...');
  assert(Item.schema.paths.reviewCount, 'Item should have reviewCount path');
  assert(Item.schema.paths.rating, 'Item should have rating path');
  console.log('✅ Test 4 Passed: Item schema reviewCount verified');

  console.log('\n🎉 ALL TESTS PASSED SUCCESSFULLY!');
}

runTests().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});

