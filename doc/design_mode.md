# CPU设计模式

这篇文档介绍了CPU设计所用的整体软件抽象，模块化方法。

基本照搬Vexriscv的设计模式，核心思想是所有模块都是Plugin，包括ALU、寄存器文件、PC组件等。每一个Plugin依赖于流水线，可以采用pipeline成员变量得到。流水线中可以获取到对应的Stage，每一个input、output、insert都要绑定到stage上，表示这个信号是在哪个stage输出。所有模块间需要传递的信号（包括流水线上储存的信号和同阶段直接连线）都在PipelineSignals里定义一个Stageable作为key，采用input、output传递，这样在编译时会自动连线。
如果需要使用子模块（即一个Plugin里直接例化），可以直接使用Component基本设计，Plugin是指在CPU顶层将被同层组装和连线的所有组件。

Vexriscv中提供了一种Service的依赖系统，然而据我观察仅用于在setup阶段收集软件信息，信号仍然采用Stageable进行连线。

## 定义CPU I/O信号

所有在流水线Plugin中指定了in或out方向的信号都是CPU的I/O信号，因为Plugin不会生成Component，而是单层扁平的。
因为I/O信号需要对外获取，所以需要绑定到Plugin组件的成员变量，其它信号可以是成员变量，也可以是build函数中的局部变量。
注意Component的io成员变量并没有任何特殊含义，除了SpinalHDL会检查其中所有信号都有in/out方向标识，会生成对外的信号接口。
由于流水线本身一般是Component，不妨将所有I/O信号都绑定到流水线的io成员变量中，便于上层组件获取。

## 流水线相关信号

流水线相关信号是自动生成的，因此不需要在Plugin中定义，找到对应stage的arbitration各信号即可。arbitration的信号含义可以参考[Stage](Loong/src/zencove/builder/Stage.scala)中声明处的注释，arbitration信号逻辑可以参考[Pipeline](Loong/src/zencove/builder/Pipeline.scala)中信号逻辑生成处的注释。此处的注释仍在完善中。

arbitration信号请使用setWhen或clearWhen方法，绕开assignment overlap报错。

## 如何使用input、output、insert

input是指一个阶段信号的输入值，通常应当作为右值，其默认值是流水线寄存的值，作为左值修改其内容也可以，但要注意assignment overlap问题。

output是指一个阶段信号的输出值，其默认值是input值，可以作为左值修改其内容，但要注意assignment overlap问题。

insert是指一个信号刚进入流水线的初值，通常应当作为左值，但也可以作为右值再次引用其信号值。

注意input值在insert的阶段默认为insert值，是有效的。

信号在其insert阶段的流向为`insert->input->output`，在后续阶段的流向为`last output (locked)->input->output`。

## 信号的名称

Spinal内置信号名称的给定在[SpinalHDL文档](https://spinalhdl.github.io/SpinalDoc-RTD/master/SpinalHDL/Structuring/naming.html)中有比较详细的叙述，以下的方法也都是基于上面的原则实现的。

首要原则是类的成员变量通常可以得到命名，函数局部变量则不能。
这里容易搞混的是匿名类，它使用起来会非常像函数，但由于是类，所以可以得到命名。
在Plugin中可以使用`stage plug new Area {}`的写法（注意大括号里成为一个匿名类了），这会使得其中的（成员）信号命名变为`{stage}_{plugin}_{signal}`。
还可以使用`pipeline plug new Area {}`，这会使得其中的信号命名变为`{plugin}_{signal}`。
直接定义Plugin的成员变量，根据SpinalHDL默认的命名规则，也是`{plugin}_{signal}`的形式。
build函数的局部变量则不能直接得到命名，因此建议直接对build函数使用plug，再写其内容。
当然，由于一个Plugin就是Nameable，使用Spinal默认的`new Composite(this) {}`与`pipeline plug new Area {}`没有什么区别。
plug的嵌套也是没有问题的，因为内层Area实际没有绑定到外层Area的成员变量。

setup过程一般不应当生成太多逻辑，所以可以不用plug。

总结一下，单阶段操作就直接开头`stage plug new Area {}`写build内容即可，
一个例外是具体在哪个stage需要根据config判断，此时还是得先`{}`在内部plug。
多阶段操作直接开头`pipeline plug new Area {}`，内部嵌套`stage plug new Area {}`，
除非不同阶段间没有共享的信号，那么可以省略`pipeline plug new Area`，否则就会造成函数体内的局部变量信号没有名称。

通过insert/input/output定义的流水线信号则有另一套命名规则。Stageable本身有名称，其默认名称是类名，同时它又通常是单例，因此常见用object定义。但是任何能够继承Stageable的方法+手动命名都是一样的效果。其流水线寄存器会被命名为`{stageBefore}_to_{stage}_{stageable}`，其input信号会被命名为`{stage}_{stageable}`，output信号则没有名称。

## setup与build

Plugin中提供了setup和build两个在生成硬件过程中调用的函数，其中setup默认空函数，build必须实现。（在少数情况下，build也会实现为空函数。）不同plugin之间的setup顺序或者build顺序不应被依赖，但是所有plugin会先统一setup，再统一build。因此在设计中，通常setup函数中注册其它plugin提供的服务，build时对应的plugin就会生成相应的硬件代码。Build函数中则生成plugin内部信号的硬件逻辑，并对已经注册的服务生成对应的硬件代码，也可以与其它plugin的已有信号进行互相连线。在万不得已的时候，还可以通过`Component.current.afterElaboration`生成一些推迟的硬件代码，保证生成代码的优先级顺序。如果遇到这种情况，更推荐通过添加中转信号的方式，使得build中可以直接完成代码生成。

## 多Pipeline（复杂流水）

在超标量下通常指令不是遵循一条流水的，中间可能会有buffer隔开，然而不同Pipeline之间仍有组件连接，若分别做成Component仍然会带来一些连线麻烦，因此考虑原则是不同的Pipeline在同一个Component下，能够互相做到服务查找。

因此最终抽象是`Pipeline`与`MultiPipeline`，前者是后者的子Pipeline。
为了保证正常的build调用，所有pipeline需要通过`addPipeline`加入`pipelines`列表中。

每一个Plugin仍有一个build context，可以是子Pipeline（local语境）也可以是MultiPipeline（global语境）。而Plugin可以在两个语境中互相查找。同pipeline内部查找仍然采用service，global查local只需要通过成员变量引用，local查global通过globalService查找。
不同子pipeline之间未实现互查找，因为跨pipeline引用的组件可以放在global语境中，在乱序执行CPU实际设计中并没有遇到麻烦。

## 定义服务接口

查找service并不总要查找Plugin本身的类型，其任何（直接或间接的）基类都可以用于查找，因此有的时候为服务定义一个trait是很方便的。这使得服务转移到另一个plugin实现时不需要改变任何对它的依赖查找。同时，这也使得在服务本身尚未完整实现时可以提供其dummy实现，那么就不需要build其完整实现对应的plugin，对于构造测试单元非常有利。
