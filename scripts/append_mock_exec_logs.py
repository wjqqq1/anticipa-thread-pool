# -*- coding: utf-8 -*-
"""Append 75 NDJSON task execution lines ([模拟调优026]-[100]) to exec_20260513.log."""
import json
import uuid
from datetime import datetime, timedelta

LOG_PATH = r"e:\code\github\anticipa-thread-pool\data\agent\task-logs\f0d9b759-5b58-48ca-b7a6-00382f13885f\exec_20260513.log"
NOTIFY_FAIL = "通知未发出：无钉钉等 NotifierService，或本分钟内同类型通知已限流（见 NotifierDispatcher 日志）"
NOTIFY_OK = "通知已提交发送"
TASK_ID = "f0d9b759-5b58-48ca-b7a6-00382f13885f"


def fmt_ts(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S.") + f"{dt.microsecond * 1000:09d}"


def main() -> None:
    raw = open(LOG_PATH, "rb").read()
    size_before = len(raw)
    nl = "\r\n" if raw.endswith(b"\r\n") else "\n"

    base = datetime(2026, 5, 13, 23, 32, 0, 0)
    out_lines = []
    for n, idx in enumerate(range(26, 101)):
        action = "AUTO_ADJUST" if (n % 2 == 0) else "NOTIFY_ONLY"
        adjusted = action == "AUTO_ADJUST"
        notified = (n % 2 == 0)
        start = base + timedelta(seconds=n * 12, milliseconds=(n * 73) % 1000)
        duration_ms = 6000 + (n * 97) % 2001
        end = start + timedelta(milliseconds=duration_ms)
        analysis = (
            f"[模拟调优{idx:03d}]"
            " 队列积压偏高，活跃线程已达上限，存在任务拒绝与排队延迟；"
            "建议评估扩大 maximumPoolSize 与队列容量，并核对拒绝计数与吞吐。"
        )
        row = {
            "action": action,
            "adjusted": adjusted,
            "analysisReport": analysis,
            "createdAt": fmt_ts(start),
            "endTime": fmt_ts(end),
            "startTime": fmt_ts(start),
            "durationMs": duration_ms,
            "logId": str(uuid.uuid4()),
            "notified": notified,
            "notifyResult": NOTIFY_OK if notified else NOTIFY_FAIL,
            "result": "SUCCESS",
            "taskId": TASK_ID,
            "taskName": "测试定时任务",
            "threadPoolId": "anticipa-order-handler",
        }
        if adjusted:
            row["adjustDetail"] = json.dumps(
                {"mock": True, "idx": idx, "before": {"maximumPoolSize": 16}, "after": {"maximumPoolSize": 32}},
                ensure_ascii=False,
            )
        out_lines.append(json.dumps(row, ensure_ascii=False))

    block = nl.join(out_lines) + nl
    block_b = block.encode("utf-8")
    with open(LOG_PATH, "ab") as f:
        if raw and not raw.endswith((b"\n", b"\r\n")):
            f.write(nl.encode("utf-8"))
        f.write(block_b)
    size_after = len(open(LOG_PATH, "rb").read())
    print("bytes_appended", size_after - size_before)
    print("last_preview", out_lines[-1][:220])


if __name__ == "__main__":
    main()
