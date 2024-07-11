# Vulkan 4 Kotlin

This is a Kotlin binding for the Vulkan API. It extends the [LWJGL](https://www.lwjgl.org/) Vulkan binding with Kotlin
features and object-oriented API. The goal is to provide a more Kotlin-friendly API for Vulkan and to make it easier to
use Vulkan in Kotlin projects.

Name subject to change.

## Status

Be aware that the API is subject to change. This project is still in the early stages of development.

I'm relatively new to Vulkan, so I may have missed some important details. This is also a hobby project, so I don't
guarantee that I'll be able to work on it consistently.

## Documentation

The documentation is still a work in progress. See [Bindings](docs/Bindings.md) for a description of how the Vulkan API
is exposed from this library.

## Project Structure

This project includes a code generator that generates the Kotlin bindings from the Vulkan API specification and
reflection data from the LWJGL Vulkan binding.

There are some standalone classes in the `src/main/kotlin` directory that are not generated.

## Building

This project uses Gradle. To build the project, run `./gradlew build`. This will download the necessary dependencies,
including a snapshot of the vulkan-docs repository and LWJGL Vulkan binding. The code generator will then generate the
Kotlin bindings and compile the project.

## Including in Your Project

Currently, I haven't published this library to any repositories. To include it in your project, you can clone this
repository and publish it to your local Maven repository. I also haven't put any effort into the dependency management.

## License

See the [LICENSE](LICENSE) file for license information.
