# Lock consumer

普通jar + JDK25，无框架/test classpath。64MiB堆、2 CPU、45秒总进程预算；5轮各50,000个近512单元新key和2,000次非法输入，回收后保留堆相对预热增长不超过8MiB且小于48MiB。随后两线程模式各16 worker验证严格3键准入和16,000次同键效果。GC后保留堆是该受控进程观测，不外推为任意宿主实时GC保证。

legacy-api来自基线0ee9d547022371ad31f885605e999de17ec22777，字节SHA见historical-sources.json。LegacyLockConsumer先对原SPI编译，运行时移除旧classes，只有新普通jar；验证旧签名链接及新容量回收行为。公开删除盘点不靠此单一样例穷举，完整迁移清单见docs/building/local-locking.md，31/33汇总账本。
