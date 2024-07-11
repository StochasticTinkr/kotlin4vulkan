# Bindings

## LWJGL

We use LWJGL as the underlying Java binding for Vulkan, and we generate kotlin code on top of that. LWJGL has its own
quirks, and we have to work around those at times.

In particular, LWJGL has the concept of AutoSized types.
Some Vulkan commands have a parameter that specifies the size of an array parameter. LWJGL uses the length of the
buffer parameter to automatically set the size parameter, which means it isn't exposed to the Java API. We will
do the same in our Kotlin API.

LWJGL defines a few handle classes, but we define the rest. We also define the `Vulkan` object. If LWJGL defines a
handle class, we use that. If LWJGL does not define a handle class, we define a wrapper class for the handle.

LWJGL also defines all the struct types that are used in the Vulkan API. We use those structs directly, but do add some
extension functions and other utility functions to make them more Kotlin friendly.

LWJGL does not define any enums or bitmasks for Vulkan. We define those ourselves. It does define the constants, but we
do not use those directly. We use the constants from the enum classes we define.

## Enums and Bitmasks

The Vulkan spec defines enums as a type that has a set of constants, and bitmasks that use the constants from an enum.
We create value classes for each enum and bitmask type. It's important to note that the enum and bitmask types are not
interchangeable. Not all enums types are bitmasks, but all bitmask types are enums.

Constant names are "cleaned" to remove unnecessary prefixes and suffixes using the following rules:

1. If the constant name contains the enum name, the enum name is removed.
2. If the constant name ends with `_BIT` before the tag, the `_BIT` is removed.
3. If the constant name ends with the same extension tag as the enum name, the extension tag is removed.
4. If none of the above rules apply, the `VK_` prefix is removed.

Examples:

- `VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT` becomes `VkImageUsageFlagBits.COLOR_ATTACHMENT`.
- `VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR` becomes `VkCompositeAlphaFlagBitsKHR.OPAQUE`.
- `VK_ERROR_OUT_OF_HOST_MEMORY` becomes `VkResult.ERROR_OUT_OF_HOST_MEMORY`.

_Note: the same constant name can be used in multiple enums. For example, VkImageUsageFlagBits and VkImageCreateFlags
both have the same constants._

### Enum class structure

```
@JvmInline
value class VkEnumType(val value: Int) {
    companion object {
        inline operator fun invoke(init: Companion.() -> VkEnumType) = this.init()
        val CONSTANT_A = EnumType(0)
        val CONSTANT_B = EnumType(1)
        // ...
    }

    val name: String get() = "..."
    override fun toString() = "VkEnumType($name)"
}
```

Note that VkResult also has for following members:

```
val isSuccess:Boolean
val isError:Boolean
val isWarning:Boolean

/**
 * Throws an exception if the result is not a success.
 */
fun reportFailure()

/**
 * Throws an exception if the result is not a success.
 */
inline fun reportFailure(lazyMessage: () -> String)
    
/**
 * Throws an exception if the result is not a success.
 */
fun reportFailure(message: String) = reportFailure { message }
```

### Bitmask class structure

```
@JvmInline
value class VkBitmaskType(val value: Int) {
    operator fun plus(other: VkBitmaskType) = VkBitmaskType(value or other.value)
    operator fun minus(other: VkBitmaskType) = VkBitmaskType(value and other.value)
    operator fun contains(other: VkBitmaskType) = value and other.value == other.value

    companion object {
        inline operator fun invoke(init: Companion.() -> VkBitmaskType) = this.init()
        val FLAG_A = BitmaskType(0)
        val FLAG_B = BitmaskType(1)
        // ...
    }

    val name: String get() = "" // A comma separated list of the names bits that are set, plus any leftover bits in hex

    override fun toString() = "VkBitMaskType($name)"
}
```

## Structs and Unions

Struct and Union classes are all defined by LWJGL. We add extension functions and utility functions to make them more
Kotlin friendly, and we add Allocator objects and builder functions for them.

For members that are the unwrapped value of a wrapped type, we provide extension properties and functions that wrap and
unwrap the value. One exception is Handles can not be wrapped without context, so we don't automatically wrap them.

Enum/Bitmask members are also given a function that takes a lambda that creates the value, within the context of the
enum or bitmask builder. This allows for a more fluent API when creating structs.

## Special Types

- `(const) void *` types are represented as `ByteBuffer` by LWJGL.
- pointer-to-pointer types are represented as `PointerBuffer` by LWJGL.
- `(const) char *` types are represented as `ByteBuffer` and `CharSequence` by LWJGL.  
  If the argument is optional and nullable, we use `ByteBuffer? = null`, and `CharSequence?` without a default.
  This prevents ambiguity when calling the function with a default value. If you do pass in a literal null, you
  will need to cast it to the correct type.
- Array types and other types that we don't explicitly handle use the underlying LWJGL type.

## Handles

Handles are the main way to interact with Vulkan objects. Some are defined by LWJGL, and we use them as is. Others are
defined by us, and we define a wrapper class for them. We also define the `Vulkan` object which can be considered the
global handle.

### Close methods
Most handles can be closed, and we provide a `close` function for them. For handles that we create that have a close
function,
we make them `AutoCloseable`. If the class is from LWJGL, we can't make it `AutoCloseable`, but we provide a `close()`
extension function, a `use(...)` extension function, and an `asAutoCloseable()` extension function, which returns a
`AutoCloseableWrapper` instance.

Note, we don't currently have support for VkAllocationCallbacks in close methods, but may add it in the future. If you
need VkAllocationCallbacks, you'll need to call the destroy function directly.

### Command Mapping

Vulkan commands are defined in C style. What we think of as a receiver in kotlin is usually the first parameter in the C
function. Also, the result of the command is often not the return value, but stored in a pointer parameter. This is
because the return value is used to indicate success or failure of the command. This is a common pattern in C APIs.

The goal of the command mapping is to make the Vulkan API more Kotlin friendly.

We only map commands that are exposed from LWJGL.

LWJGL provides overloads with array types for some commands, but we don't expose those.

Parameter types are mapped depending on the category of the type. See the sections below.

We provide at least one method for every supported command, and may provide additional overloads depending on the type
of command. For LWJGL methods that have overloads between `ByteBuffer` and `CharSequence`,
we expose both overloads as well.

### Command Names

We "clean" the command names to remove unnecessary prefixes and suffixes using the following rules:

1. If the command name contains the receiver type name, or the receivers parent type name, that name is removed.
2. The `vk` prefix is removed.
3. The first letter is lowercased.

Examples:

- `vkCreateInstance` -> `Vulkan.createInstance`
- `vkCreateBuffer` -> `VkDevice.createBuffer`
- `vkGetPhysicalDeviceQueueFamilyProperties` -> `VkPhysicalDevice.getQueueFamilyProperties`

### Command Receivers

The receiver of a command is a handle, or the Vulkan object. We look at the first two parameters of the command to
determine the receiver.

1. If the first parameter is not a handle, the receiver is the `Vulkan` object.
2. If the first parameter is a handle, we create a function (or extension function) on the handle class.
3. If the first parameter is a parent handle of the second parameter, we _also_ create a function (or extension
   function)
   on the second parameter's handle class.

This does mean that some commands are available in multiple places. For example, `vkCreateBuffer` is available on both
`VkDevice` and `VkPhysicalDevice`, though with different parameters.

### VkResult checks

If the command returns a VkResult, we call `reportFailure` on the result, which may throw an exception. The type of
command determines the method return type. See the sections below for details.

### Parameter types

For input parameters, we use the following rules:

1. We use the `@Nullable` annotation from LWJGL to determine if the parameter is nullable.
2. If the parameter is a handle, we accept the handle class. If nullable, default to `null`.
3. If the parameter is an enum or bitmask type, we accept a lambda which has the context of the builder.
4. If the parameter is a struct/union, we accept a lambda which has the context of the builder and `OnStack`. If
   optional, we default to `{ null }`.
5. If the parameter is a `char *`, we accept the underlying LWJGL type. See [Special Types](#special-types) for details
   on nullability.
6. Any type that we don't support, is accepted as the underlying LWJGL type.

For output parameters, see the sections below.

### Command types

There are a few types of commands in Vulkan. Most commands return either void or a VkResult, regardless of the type of
command.

1. Create/Allocate commands - These commands create a new Vulkan object, or allocate a resource. These are treated as
   "Single value get commands" in our API. They always have a single output parameter, and a VkAllocationCallbacks
   parameter.
2. Destroy/Free commands - These commands destroy a Vulkan object, or free a resource
   These are mapped to the `close` function for the handle class, but also exposed as a command.
   They always have a VkAllocationCallbacks parameter. `close` will always pass `null` for the VkAllocationCallbacks.
3. Single value get commands - These commands return a single value, into a pointer parameter.
4. Enumerating commands - These commands have a pointer to a count parameter, and a pointer to an array parameter.
5. Memory Map commands - There are 2 commands for mapping device memory on the host.
6. Other commands - All other commands that don't fit into the above categories.

#### Single value get commands

Create, allocate, and single output value commands are handled the same way. What they look like depends on the output type.

Handles outputs
: For commands that output a handle, we provide one overload that returns the wrapped handle type.
: This includes outputs that are a list of handles, in which case we return a List of the wrapped handle type.

Enum/Bitmask outputs
: For commands that output an enum or bitmask, we provide one overload that returns the wrapped enum.

Structure/Unions outputs
: For commands that output into a structure, we provide different overloads for the different ways to allocate
the result structure.

1. The last parameter is a `MemoryStack` and the result is allocated on the stack.
2. The last parameter is the singleton `Heap` object and the result is allocated on the heap.
3. The last parameter is a `()->Result` or `(Int)->Result` lambda that creates the result object.
4. The last parameter is a pre-allocated result object. This overload will return a VkResult if the underlying LWJGL method
   returns a VkResult.

VkBool32 output 
: For commands that output a VkBool32, we provide an overload that returns a `Boolean`.

VkDeviceSize and VkRemoteAddressNV output
: For commands that output a VkDeviceSize or VkRemoteAddressNV, we provide an overload that returns a `Long`.

#### Destroy/Free commands

Destroy commands are exposed as a normal command, but also mapped to the `close` function for the handle class. See
[Close Methods](#close-methods) in handles for more details on close handling.

#### Memory Map commands

For vkMapMemory and vkMapMemory2KHR, we provide the following overloads:

1. One that returns a `ByteBuffer`. The memory will need to be manually unmapped.
2. One that accepts a lambda that takes the `ByteBuffer` and returns a value. The memory will be automatically unmapped
   when the lambda completes.

#### Enumerating commands

One thing all enumerating commands have in common is that they have a pointer to a count parameter. We provide
overloads that will determine the count, and allocate the output, so the user doesn't have to worry about it.
We provide these different types of overloads, but not all are available for every output type:

1. The last parameter is a `MemoryStack` and the result is allocated on the stack. Available for all types.
2. The last parameter is the singleton `Heap` object and the result is allocated on the heap. Available for all types.
3. The last parameter is a `(Int)->Buffer` lambda that creates the result buffer. Available for all types.
4. The last parameter is a pre-allocated buffer. This overload will return a VkResult if the underlying LWJGL method
   returns a VkResult. Available for all types.
5. A command that returns an `CompleteEnumerable` object, which can be used to iterate over the results without needing
   to handle the allocation of the buffer explicitly. Available for Structs and Unions.
6. A command that returns a List of the results. Available for Enums, Bitmasks, and Handles.

We also provide function with the prefix `num` replacing `get` or `enumerate` that returns the count.

#### Other commands

We don't add additional overloads for other commands. The return type is the wrapped return type of the LWJGL method.
If the return type is `void`, we return `Unit`.
