# Load & Performance Testing (NFR-2)

This directory contains the load testing suite for **NFR-2**:
> **Response Time:** p95 below 500 ms for create, get, and cancel at 20 concurrent users with at least 10,000 seeded appointments.

---

## Prerequisites

1. **Application running**: Start the database and application:
   ```bash
   docker compose up -d
   ```
   Or run the database in Docker and the app locally:
   ```bash
   docker compose up -d db
   ./mvnw spring-boot:run
   ```

2. **k6 installed**:
   - macOS: `brew install k6`
   - Windows: `scoop install k6` or `winget install k6`
   - Linux: `sudo apt install k6`
   - Alternatively via Docker: `docker run --rm -i --net=host grafana/k6 run - <booking-load.js`

---

## Step 1: Seed 10,000+ Appointments

Seed the database with 10,800 confirmed historical appointments across 1,000 customers and 1,000 vehicles:

**If running Docker Compose:**
```bash
docker compose exec -T db psql -U klasm -d klasm < load-test/seed-load-data.sql
```

**If running local PostgreSQL:**
```bash
psql -h localhost -p 5432 -U klasm -d klasm -f load-test/seed-load-data.sql
```

Verify appointment count:
```sql
SELECT count(*) FROM appointment;
-- Output: >= 10800
```

---

## Step 2: Run the k6 Load Test

Execute the 20-virtual-user test for 30 seconds:

```bash
k6 run load-test/booking-load.js
```

Or targeting a custom base URL:
```bash
k6 run -e BASE_URL=http://localhost:8080 load-test/booking-load.js
```

---

## Step 3: Interpret Results & Thresholds

The script defines thresholds matching NFR-2:
- `http_req_duration`: `p(95) < 500ms` overall
- `http_req_duration{endpoint:create_booking}`: `p(95) < 500ms`
- `http_req_duration{endpoint:get_appointment}`: `p(95) < 500ms`
- `http_req_duration{endpoint:list_appointments}`: `p(95) < 500ms`
- `http_req_duration{endpoint:cancel_appointment}`: `p(95) < 500ms`

All endpoints will be reported in the k6 summary output with their p90, p95, and average latencies.
