const https = require('https');

function httpRequest(url, options, bodyData) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          resolve({ status: res.statusCode, headers: res.headers, body: parsed });
        } catch (e) {
          resolve({ status: res.statusCode, headers: res.headers, raw: data });
        }
      });
    });
    req.on('error', reject);
    if (bodyData) {
      req.write(typeof bodyData === 'string' ? bodyData : JSON.stringify(bodyData));
    }
    req.end();
  });
}

async function testRenderLiveApi() {
  const baseUrl = 'https://quickbite-server-pjaf.onrender.com';
  console.log('Testing Render live backend:', baseUrl);

  // 1. Health check
  console.log('\n--- 1. Health Check ---');
  const health = await httpRequest(`${baseUrl}/health`, { method: 'GET' });
  console.log('Health response:', health.status, health.body);

  // 2. Place Order via POST /api/orders
  console.log('\n--- 2. Place Live Order via Render API ---');
  const testOrderId = 'ORD-LIVE-TEST-' + Date.now();
  const idempotencyKey = 'IDEMPOTENT-' + Date.now();

  const newOrderPayload = {
    orderId: testOrderId,
    idempotencyKey: idempotencyKey,
    studentId: '12314501', // Real student registration number
    studentName: 'santosh',
    studentPhone: '9456312870',
    studentCourse: 'ECE',
    canteenId: 'canteen_33',
    items: [
      {
        itemId: 'c33_item_003_chana_kulcha',
        name: 'Chana Kulcha',
        price: 75,
        quantity: 1,
        prepMinutes: 6
      }
    ],
    totalAmount: 75,
    status: 'NEW',
    tokenNumber: '888',
    pickupPreference: 'Pickup ASAP',
    customerPickupTime: 'ASAP',
    pickupCanteenName: "Govinda's Kitchen",
    pickupLocation: 'Block 33 (6th Floor)',
    pickupCounter: 'Counter 1'
  };

  const createRes = await httpRequest(`${baseUrl}/api/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'idempotency-key': idempotencyKey
    }
  }, newOrderPayload);

  console.log('Order creation response code:', createRes.status);
  console.log('Created order ID:', createRes.body?.orderId, 'Verification token:', createRes.body?.verificationToken);

  // 3. Verify retrieval by querying GET /api/orders/:orderId
  console.log('\n--- 3. Query Order via Render API ---');
  const fetchRes = await httpRequest(`${baseUrl}/api/orders/${testOrderId}`, { method: 'GET' });
  console.log('Fetched order exists in DB:', fetchRes.status === 200);
  console.log('Stored in MongoDB fields:', {
    orderId: fetchRes.body?.orderId,
    studentId: fetchRes.body?.studentId,
    status: fetchRes.body?.status,
    totalAmount: fetchRes.body?.totalAmount,
    items: fetchRes.body?.items?.length,
    statusHistory: fetchRes.body?.statusHistory?.map(h => h.status)
  });

  // 4. Update order status to PREPARING via PATCH /api/orders/:orderId/status
  console.log('\n--- 4. Update Status to PREPARING ---');
  const patchRes = await httpRequest(`${baseUrl}/api/orders/${testOrderId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' }
  }, { status: 'PREPARING' });

  console.log('PATCH status response:', patchRes.status, 'New status:', patchRes.body?.status);

  // 5. Query again to verify statusHistory and status updated in MongoDB
  const fetchUpdated = await httpRequest(`${baseUrl}/api/orders/${testOrderId}`, { method: 'GET' });
  console.log('Re-queried MongoDB order status:', fetchUpdated.body?.status);
  console.log('statusHistory in DB:', fetchUpdated.body?.statusHistory?.map(h => `${h.status} at ${h.timestampLocal}`));

  // 6. Test Idempotency with same key
  console.log('\n--- 6. Test Idempotency Retry via Render API ---');
  const retryRes = await httpRequest(`${baseUrl}/api/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'idempotency-key': idempotencyKey
    }
  }, newOrderPayload);

  console.log('Retry response code:', retryRes.status);
  console.log('Idempotency returned cached order:', retryRes.body?.orderId === testOrderId);

  console.log('\n🎉 RENDER PRODUCTION API PERSISTENCE TESTS COMPLETED!');
}

testRenderLiveApi().catch(err => {
  console.error('Render test failed:', err);
  process.exit(1);
});

