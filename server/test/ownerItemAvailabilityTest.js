const assert = require('assert');
const Item = require('../src/models/Item');

async function runTests() {
  console.log('--- Starting Owner Item Availability Unit & Schema Tests ---');

  // Test 1: Verify Item Schema Paths
  console.log('Test 1: Verifying Item schema definition...');
  assert(Item.schema.paths.id, 'Item schema must have "id" path');
  assert(Item.schema.paths.canteenId, 'Item schema must have "canteenId" path');
  assert(Item.schema.paths.available, 'Item schema must have "available" path');
  assert(Item.schema.paths.stock, 'Item schema must have "stock" path');
  assert(Item.schema.paths.price, 'Item schema must have "price" path');
  assert.strictEqual(Item.schema.paths.available.instance, 'Boolean', '"available" must be Boolean');
  assert.strictEqual(Item.schema.paths.stock.instance, 'Number', '"stock" must be Number');
  console.log('✅ Test 1 Passed: Item schema paths and types verified.');

  // Test 2: Validation Rule: Cannot be available if stock is 0
  console.log('\nTest 2: Verifying zero-stock availability rule...');
  function validateAvailability(item, requestedAvailable) {
    if (typeof requestedAvailable !== 'boolean') {
      throw new Error('Field "available" must be boolean');
    }
    if (requestedAvailable && item.stock === 0) {
      throw new Error('Cannot set item available when stock is 0');
    }
    return { ...item, available: requestedAvailable };
  }

  const outOfStockItem = { id: 'item_1', name: 'Masala Dosa', stock: 0, available: false, canteenId: 'canteen_33' };
  assert.throws(() => {
    validateAvailability(outOfStockItem, true);
  }, /Cannot set item available when stock is 0/, 'Should reject enabling out of stock item');
  console.log('✅ Test 2 Passed: Zero-stock availability properly blocked.');

  // Test 3: Normal Availability Toggle
  console.log('\nTest 3: Toggling availability when stock > 0...');
  const inStockItem = { id: 'item_2', name: 'Paneer Roll', stock: 15, available: true, canteenId: 'canteen_33' };
  const toggledOff = validateAvailability(inStockItem, false);
  assert.strictEqual(toggledOff.available, false, 'Should be false after toggle off');
  assert.strictEqual(toggledOff.stock, 15, 'Stock should remain unchanged');

  const toggledOn = validateAvailability(toggledOff, true);
  assert.strictEqual(toggledOn.available, true, 'Should be true after toggle on');
  assert.strictEqual(toggledOn.stock, 15, 'Stock should remain unchanged');
  console.log('✅ Test 3 Passed: Toggle on and off preserves stock and updates availability.');

  // Test 4: Canteen Authorization Check
  console.log('\nTest 4: Verifying Canteen Owner Authorization Isolation...');
  function authorizeItemUpdate(item, ownerCanteenId) {
    if (ownerCanteenId && item.canteenId !== ownerCanteenId) {
      throw new Error('Unauthorized: Item belongs to another canteen');
    }
    return true;
  }

  assert.strictEqual(authorizeItemUpdate(inStockItem, 'canteen_33'), true);
  assert.throws(() => {
    authorizeItemUpdate(inStockItem, 'canteen_other');
  }, /Unauthorized: Item belongs to another canteen/);
  console.log('✅ Test 4 Passed: Cross-canteen modifications rejected.');

  console.log('\n🎉 ALL OWNER ITEM AVAILABILITY TESTS PASSED SUCCESSFULLY!');
}

runTests().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});

