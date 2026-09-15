/**
 * Campus Local Time Helper (Indian Standard Time, IST = UTC+5:30)
 * 
 * MongoDB BSON Date objects are ALWAYS stored in true UTC (epoch milliseconds).
 * This helper provides formatting functions to render UTC timestamps into
 * local campus time (IST) strings for display.
 */

const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Returns a standard UTC Date object representing current server time.
 * Stored directly in MongoDB BSON Date fields.
 */
function getUtcDate(date = new Date()) {
  const d = date instanceof Date ? date : new Date(date);
  return isNaN(d.getTime()) ? new Date() : d;
}

/**
 * Backward-compatible alias for getUtcDate.
 * Does NOT artificially shift UTC epoch, preserving correct UTC timestamps.
 */
function getIstDate(date = new Date()) {
  return getUtcDate(date);
}

/**
 * Formats a UTC date into a full human-readable local IST timestamp:
 * e.g. "12 Sep 2026, 01:00:14 AM"
 */
function formatIstDateTime(date = new Date()) {
  const d = getUtcDate(date);
  const ist = new Date(d.getTime() + IST_OFFSET_MS);

  const day = ist.getUTCDate();
  const month = MONTH_NAMES[ist.getUTCMonth()];
  const year = ist.getUTCFullYear();
  let hours = ist.getUTCHours();
  const minutes = ist.getUTCMinutes();
  const seconds = ist.getUTCSeconds();
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  hours = hours ? hours : 12;

  const hStr = hours < 10 ? '0' + hours : hours;
  const mStr = minutes < 10 ? '0' + minutes : minutes;
  const sStr = seconds < 10 ? '0' + seconds : seconds;

  return `${day} ${month} ${year}, ${hStr}:${mStr}:${sStr} ${ampm}`;
}

/**
 * Formats a UTC date into 12-hour AM/PM IST time: e.g. "1:00 AM"
 */
function formatIstTime(date = new Date()) {
  const d = getUtcDate(date);
  const ist = new Date(d.getTime() + IST_OFFSET_MS);

  let hours = ist.getUTCHours();
  const minutes = ist.getUTCMinutes();
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  hours = hours ? hours : 12;
  const mStr = minutes < 10 ? '0' + minutes : minutes;

  return `${hours}:${mStr} ${ampm}`;
}

/**
 * Formats a UTC date into IST date string: e.g. "12 Sep 2026"
 */
function formatIstDate(date = new Date()) {
  const d = getUtcDate(date);
  const ist = new Date(d.getTime() + IST_OFFSET_MS);

  const day = ist.getUTCDate();
  const month = MONTH_NAMES[ist.getUTCMonth()];
  const year = ist.getUTCFullYear();

  return `${day} ${month} ${year}`;
}

/**
 * Add minutes to a UTC date safely
 */
function addMinutes(date, minutes) {
  const d = getUtcDate(date);
  return new Date(d.getTime() + minutes * 60000);
}

module.exports = {
  IST_OFFSET_MS,
  getUtcDate,
  getIstDate,
  formatIstDateTime,
  formatIstTime,
  formatIstDate,
  addMinutes,
};
