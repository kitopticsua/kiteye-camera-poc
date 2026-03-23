# CAM-2 — Frame Pipeline (Triple Buffer)

## Goal
Implement lock-free `TripleBuffer` for zero-allocation frame passing from USB thread to GL thread. Pre-allocate 3 × 614,400-byte slots at stream start.

## Type
android (Kotlin, pipeline)

## Priority
P0 — required by CAM-3 (streaming) and CAM-4 (rendering)

## Dependencies
- CAM-1 (needs UsbCameraState to know when to allocate)

## Acceptance Criteria
- [ ] `TripleBuffer` pre-allocates exactly 3 × 614,400 bytes (640×480×2 for YUYV) at construction
- [ ] `write(ByteArray)` copies frame into next available slot (USB thread)
- [ ] `read(): ByteBuffer?` returns latest complete frame (GL thread), null if none ready
- [ ] Lock-free swap via `AtomicInteger` (no `synchronized`)
- [ ] No allocations after construction (verified by test)
- [ ] `FrameBuffer` wrapper: tracks frame index, timestamp, size
- [ ] `FrameDistributor` wires USB callback → TripleBuffer → GL renderer

## Test Plan
### Unit Tests
| ID | Test | Expected |
|---|---|---|
| UT-01 | Buffer allocated: 3 × 614400 bytes | total ~1.8MB |
| UT-02 | write() then read() returns same data | byte-for-byte equal |
| UT-03 | Two writes, read returns latest | second write data returned |
| UT-04 | read() before any write returns null | null returned |
| UT-05 | write() does not allocate new objects | no GC during 100 calls |
| UT-06 | Concurrent write + read — no data corruption | data integrity maintained |

## Files to Create
- `pipeline/TripleBuffer.kt`
- `pipeline/FrameBuffer.kt`
- `pipeline/FrameDistributor.kt`
- `pipeline/test/TripleBufferTest.kt`

## Definition of Done
- [ ] All 6 unit tests pass
- [ ] Lint clean
- [ ] Zero allocations in write/read hot path (confirmed by test)
