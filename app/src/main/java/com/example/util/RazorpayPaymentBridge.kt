package com.example.util

import android.app.Activity
import android.util.Log
import com.razorpay.Checkout
import com.razorpay.PaymentData
import org.json.JSONObject

data class PaymentSuccessResult(
  val paymentId: String,
  val orderId: String,
  val signature: String,
)

data class PaymentFailureResult(
  val code: Int,
  val message: String,
)

object RazorpayPaymentBridge {
  private const val TAG = "RazorpayBridge"
  private var onSuccessCallback: ((PaymentSuccessResult) -> Unit)? = null
  private var onErrorCallback: ((PaymentFailureResult) -> Unit)? = null

  fun startPayment(
    activity: Activity,
    keyId: String,
    razorpayOrderId: String,
    amountInPaise: Int,
    canteenName: String,
    customerName: String,
    customerEmail: String,
    customerPhone: String,
    onSuccess: (PaymentSuccessResult) -> Unit,
    onError: (PaymentFailureResult) -> Unit,
  ) {
    this.onSuccessCallback = onSuccess
    this.onErrorCallback = onError

    try {
      Checkout.preload(activity.applicationContext)
      val checkout = Checkout()
      checkout.setKeyID(keyId)

      val options = JSONObject().apply {
        put("name", canteenName.ifBlank { "Campus Canteen" })
        put("description", "Food Order Checkout")
        put("currency", "INR")
        put("amount", amountInPaise)
        if (razorpayOrderId.isNotBlank()) {
          put("order_id", razorpayOrderId)
        }
        put("theme.color", "#F59E0B") // Matches DrawerAmber brand color

        val prefill = JSONObject().apply {
          if (customerEmail.isNotBlank()) put("email", customerEmail)
          if (customerPhone.isNotBlank()) put("contact", customerPhone)
          if (customerName.isNotBlank()) put("name", customerName)
        }
        put("prefill", prefill)

        val retryObj = JSONObject().apply {
          put("enabled", true)
          put("max_count", 2)
        }
        put("retry", retryObj)
      }

      checkout.open(activity, options)
    } catch (e: Exception) {
      Log.e(TAG, "Error starting Razorpay checkout: ${e.message}", e)
      onError(
        PaymentFailureResult(
          code = -1,
          message = e.localizedMessage ?: "Failed to launch payment checkout",
        )
      )
    }
  }

  fun notifySuccess(paymentId: String?, paymentData: PaymentData?) {
    val pId = paymentId ?: paymentData?.paymentId ?: ""
    val oId = paymentData?.orderId ?: ""
    val sig = paymentData?.signature ?: ""
    Log.d(TAG, "Payment success callback: paymentId=$pId, orderId=$oId")
    onSuccessCallback?.invoke(
      PaymentSuccessResult(
        paymentId = pId,
        orderId = oId,
        signature = sig,
      )
    )
    clearCallbacks()
  }

  fun notifyError(code: Int, response: String?, paymentData: PaymentData?) {
    Log.e(TAG, "Payment error callback: code=$code, response=$response")
    val msg = try {
      if (!response.isNullOrBlank() && response.startsWith("{")) {
        val json = JSONObject(response)
        val err = json.optJSONObject("error")
        err?.optString("description") ?: response
      } else {
        response ?: "Payment was cancelled or failed"
      }
    } catch (_: Exception) {
      response ?: "Payment was cancelled or failed"
    }
    onErrorCallback?.invoke(
      PaymentFailureResult(
        code = code,
        message = msg,
      )
    )
    clearCallbacks()
  }

  private fun clearCallbacks() {
    onSuccessCallback = null
    onErrorCallback = null
  }
}
