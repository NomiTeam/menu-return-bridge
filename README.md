# MenuReturnBridge

MenuReturnBridge 1.1.1 只负责菜单返回按钮与路由。插件名和 PDC 键 `menureturnbridge:return_to_menu` 保持兼容；按钮采用 FLOW_BANNER_PATTERN、粉色粗体「↩ 返回上层」和非斜体浅黄色说明。

支持 FarmersDelight 精确标题「农夫乐事 · 配方」的箱式菜单，以及 PlayerInv 原生 `com.playerinv.Util.InvHolder.VaultMenuHolder` 的槽 53。功能容器不处理；上一页、下一页和退出公会不替换。菜单关闭、切换、玩家退出会取消刷新与待执行返回任务；所有玩家库存访问与延迟导航使用玩家实体调度器。

MyPet 4.0.4 原生菜单仅处理 `nuomi-return`、`nuomi-backpack`、`nuomi-skilltree`、`nuomi-shop`。子菜单返回先调用原生父栈。`nuomi-reset` 和未知动作在取消事件之前即被忽略，由各自插件处理。

依赖 Purpur 26.3 的 Paper/Folia API。可选集成只有 MyPet 4.0.4、TrMenu、FarmersDelight 和 PlayerInv；未安装时不启用对应功能。TrMenu 使用原菜单 API 的两种命名空间兼容入口，缺失或不兼容会给出错误说明。其他插件版本不作兼容承诺。

命令：

- `/nuomimenu [Menu|Activity|VipControl|Sponsor|DailyQuestReset]`：仅为执行者打开已配置的 TrMenu 菜单，默认 Menu。此处名称是导航目标，不执行会员或任务业务。
- `/menureturn audit`：执行导航边界断言，并在每位玩家自身实体线程检查当前菜单的 PDC 槽边界，记录审计结果。
- `/menureturn selftest`：执行导航命名、容器边界和 MyPet 路由白名单的真实断言。

`menureturn.audit` 默认仅管理员；公开菜单入口不额外限制权限，目标菜单自行控制访问。插件没有会员、头像购买、任务重置、宠物重置或收费代码，也不注册相应业务命令。

使用 Python 3 与 PATH 中的 JDK 25，外部依赖目录应包含 Purpur 26.3 build 2654 stable API、MyPet 4.0.4 和所需 API 库（包括 Adventure、Guava、注解库）。不下载或打包第三方 JAR。

```sh
python3 build.py --deps-dir .work/input
# 或先设置 NOMI_DEPS_DIR，再执行 python3 build.py
```

构建执行 Java 断言测试后输出 `build/MenuReturnBridge-1.1.1.jar`。源码和测试均可公开复现；`.work`、构建文件及依赖均被忽略。

验证范围为真实依赖编译与领域回归；没有完成 Minecraft 内点击、TrMenu 展示、FarmersDelight/PlayerInv 实例或多玩家 Folia 体验验证。`folia-supported: true` 表示使用实体调度 API，不代表已经完成这些联调。

迁移时由安装维护者替换旧 Bridge，清理旧 Bridge 中会员、头像、任务和宠物重置配置及多余权限；将业务命令的菜单动作指向各自新插件。MyPet 的 `nuomi-reset-confirm` 注册和价格配置归 NomiPetExtras，不能同时加载仍含重置模块的旧 Bridge。不要删除公共菜单导航本身；大厅保护不属于本项目。

本仓库新实现与测试不包含原源码或第三方二进制；外部参考与依赖的版权及许可仍归各自权利人，本仓库不为其授予许可证。


源码职责：`MenuReturnBridge` 装配集成与会话事件；`InventorySession` 管理一次窗口的边界、刷新、审计和任务取消；`ReturnButton` 保持 PDC 与按钮样式；`Navigation` 保存纯导航规则；`PetNavigation` 使用 MyPet 原生菜单及父栈；`PublicMenus` 校验公开目标；`TrMenuGateway` 隔离双命名空间固定反射 API。

原生优先：MyPet 已使用 `MenuInstanceImpl.popBack()`，没有维护第二份父栈。已核验 TrMenu 3.12.5 真实 JAR 与参考源码：菜单内导航可用 `open: PersonalInfo`；`Bindings.Commands` 监听玩家命令预处理事件，不能仅凭绑定就保证其他插件直接派发命令时也可用；`RegisterCommands` 可注册玩家执行的命令并使用原生 `open:` 动作，不需要授予通用 `trmenu.command.open` 权限。通用 `trmenu open` 还支持指定其他玩家，不能作为公开本人入口放权。

PersonalInfo 的本人入口使用 TrMenu 原生 `RegisterCommands` 注册固定命令，并执行 `open: PersonalInfo`；称号仓库由后端指向该原生命令。本桥公共白名单仍为原有五项，不支持 `/nuomimenu PersonalInfo`，也不接管称号业务槽。后端新增配置不构成扩展桥接的理由。settings 注册配置通过 TrMenu 原生 `trmenu reload` 加载，需分别验证普通玩家权限、实际称号返回派发、本人会话及 Folia 行为；本仓未修改或加载生产配置。
