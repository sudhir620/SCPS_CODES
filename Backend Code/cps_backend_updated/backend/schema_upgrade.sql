-- Optional schema improvements for S3 + better statuses + IoT
-- Run this on your existing `car_parking` database

-- 1. Ensure vehicle_details.image can store full S3 URL
ALTER TABLE vehicle_details
  MODIFY COLUMN image VARCHAR(1000) DEFAULT NULL;

-- 2. Better slot status (keep is_available for backward compatibility)
-- 0 = occupied, 1 = available, 2 = reserved/booking-in-progress, 3 = out_of_service
-- (already using is_available = 2 in socket code)

-- 3. Add price_per_hour at parking lot level (optional)
ALTER TABLE parking_lots
  ADD COLUMN IF NOT EXISTS price_per_hour DECIMAL(10,2) DEFAULT 20.00 AFTER total_slots;

-- 4. Booking status clarity (existing uses int: keep as-is)
-- Recommended meaning:
-- 0 = cancelled, 1 = active/upcoming, 2 = completed, 3 = overstayed

-- 5. OTP expiry support (optional)
ALTER TABLE otp_verify
  ADD COLUMN IF NOT EXISTS expires_at DATETIME DEFAULT NULL AFTER otp;

-- 6. Gate commands table (for NodeMCU polling / push)
CREATE TABLE IF NOT EXISTS gate_commands (
  id INT AUTO_INCREMENT PRIMARY KEY,
  parking_id INT NOT NULL,
  gate ENUM('entry','exit') NOT NULL,
  command ENUM('open','close') DEFAULT 'open',
  processed TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  processed_at DATETIME NULL,
  INDEX idx_pending (parking_id, gate, processed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Index for ANPR matching
ALTER TABLE vehicle_details
  ADD INDEX IF NOT EXISTS idx_number (number);

