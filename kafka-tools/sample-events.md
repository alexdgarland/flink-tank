# Sample Events

This file contains valid JSON events for testing the Flink event processor job.

## sample-events.json

Contains 10 valid events following the `InputEvent` schema:

- **evt-001 to evt-003**: User actions (click, pageview, scroll)
- **evt-004, evt-007**: Purchase events with different currencies
- **evt-005**: User search action
- **evt-006**: System event (cache clear)
- **evt-008**: Another user click
- **evt-009**: Error event (will be filtered out by job since type="error")
- **evt-010**: User logout

All events have:
- Unique `id`
- Valid `type`
- Unix timestamp in milliseconds
- `data` object with various fields

## Usage

```bash
# Load all sample events into input-events topic
uv run ktool produce input-events --file sample-events.json
```

The file format is JSONL (one JSON object per line), which `ktool` reads line-by-line and sends as separate messages to Kafka.

## Creating Your Own Test Data

To create additional test events, add new lines to `sample-events.json` following the schema:

```json
{"id":"evt-XXX","type":"event-type","timestamp":1733328000000,"data":{"key":"value"}}
```

Keep each event on a single line (no pretty-printing within the file).
