# -*- coding: utf-8 -*-
import json
import uuid
import random
from datetime import datetime, timedelta

PATH = r"E:\code\github\anticipa-thread-pool\data\agent\task-logs\f0d9b759-5b58-48ca-b7a6-00382f13885f\exec_20260513.log"
TASK_ID = "f0d9b759-5b58-48ca-b7a6-00382f13885f"
TASK_NAME = "测试定时任务"
POOL = "anticipa-order-handler"


def fmt_ts(dt: datetime, salt: int) -> str:
    frac = (dt.microsecond * 1000 + salt) % 1000000000
    return dt.strftime("%Y-%m-%d %H:%M:%S") + f".{frac:09d}"


def main() -> None:
    random.seed(20260513)
    templates_saturation = [
        "线程池 {pool} 监控显示活跃线程数持续打满（active≈{a}，max={mx}），队列深度 {q}/{cap}（约 {pct}%），"
        "任务排队延迟上升，存在吞吐瓶颈；建议提高 maximumPoolSize 或扩大 queueCapacity 并观察拒绝率。",
        "最近 15 分钟 {pool} 的 poolSize 长期等于 maximumPoolSize（{mx}），queueSize 维持在 {q} 以上，队列利用率约 {pct}%，"
        "历史峰值显示等待占比升高；调优方向：适度增大核心/最大线程或扩容队列以削峰。",
        "{pool} 出现线程饥饿迹象：activeCount≈{a}，队列剩余容量趋近于 0，completedTaskCount 增速放缓；"
        "建议调大 maxPoolSize 与队列，并排查上游突发流量。",
    ]
    templates_reject = [
        "监控显示 {pool} 触发拒绝策略，rejected 计数在窗口内上升；当前 max={mx}、队列接近满载（{q}/{cap}）。"
        "建议紧急扩大队列或 max 线程，并启用降级/限流。",
        "{pool} 在高并发窗口内多次任务拒绝，队列 {q}/{cap}（{pct}%）；需调优线程池参数并结合业务限流。",
    ]
    templates_idle = [
        "线程池 {pool} 活跃线程长期为 0，队列空闲，资源利用率偏低；可考虑下调 corePoolSize 以节省资源（非紧急）。",
    ]
    base = datetime(2026, 5, 13, 23, 0, 0)
    lines: list[str] = []

    for i in range(100):
        t0 = base + timedelta(seconds=37 * i + (i % 7) * 13)
        dur = 4200 + (i * 137) % 25000 + random.randint(0, 800)
        t1 = t0 + timedelta(milliseconds=dur)
        log_id = str(uuid.uuid4())

        r = i % 10
        if r < 4:
            action = "AUTO_ADJUST"
            adjusted = i % 3 != 1
            a, mx, q, cap, pct = 14 + (i % 4), 16, 800 + (i % 200) * 3, 1000, 85 + (i % 14)
            analysis = random.choice(templates_saturation).format(
                pool=POOL, a=a, mx=mx, q=q, cap=cap, pct=pct
            )
            notified = i % 2 == 0
        elif r < 6:
            action = "AUTO_ADJUST"
            adjusted = i % 4 != 0
            mx, q, cap, pct = 16, 950 + i, 1000, 95
            analysis = random.choice(templates_reject).format(
                pool=POOL, mx=mx, q=q, cap=cap, pct=pct
            )
            notified = True
        elif r < 8:
            action = "NOTIFY_ONLY"
            adjusted = False
            analysis = random.choice(templates_idle).format(pool=POOL)
            notified = i % 3 == 0
        else:
            action = "NOTIFY_ONLY"
            adjusted = False
            a, mx, q, cap, pct = 12, 32, 400 + i * 2, 2000, 55 + (i % 20)
            analysis = random.choice(templates_saturation).format(
                pool=POOL, a=a, mx=mx, q=q, cap=cap, pct=pct
            )
            notified = i % 2 == 1

        notify_result = (
            "通知已提交发送"
            if notified
            else "通知未发出：无钉钉等 NotifierService，或本分钟内同类型通知已限流（见 NotifierDispatcher 日志）"
        )

        row = {
            "action": action,
            "adjusted": adjusted,
            "analysisReport": analysis,
            "createdAt": fmt_ts(t1, i),
            "durationMs": dur,
            "endTime": fmt_ts(t1, i + 1),
            "logId": log_id,
            "notified": notified,
            "notifyResult": notify_result,
            "result": "SUCCESS",
            "startTime": fmt_ts(t0, i + 2),
            "taskId": TASK_ID,
            "taskName": TASK_NAME,
            "threadPoolId": POOL,
        }
        if adjusted:
            detail = {
                "before": {"corePoolSize": 4, "maximumPoolSize": 16, "queueCapacity": 256},
                "after": {"corePoolSize": 8, "maximumPoolSize": 32, "queueCapacity": 1024},
                "changes": [
                    "maximumPoolSize 16→32",
                    "queueCapacity 256→1024",
                    "corePoolSize 4→8",
                ],
                "reason": "队列高水位+活跃线程打满，自动扩容（模拟数据）",
                "mockBatchIndex": i,
            }
            row["adjustDetail"] = json.dumps(detail, ensure_ascii=False)

        lines.append(json.dumps(row, ensure_ascii=False) + "\n")

    with open(PATH, "a", encoding="utf-8") as f:
        f.writelines(lines)
    print("appended", len(lines), "lines")


if __name__ == "__main__":
    main()
