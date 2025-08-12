package dev._100media.gradleutils

import org.codehaus.groovy.control.io.NullWriter
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.process.ExecOperations
import org.gradle.util.internal.GUtil
import org.gradle.work.DisableCachingByDefault

import javax.annotation.Nullable
import javax.inject.Inject
import java.util.stream.Collectors

@DisableCachingByDefault
abstract class ObfuscateJar extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getInputFile()

    @OutputFile
    abstract RegularFileProperty getArchiveFile()

    @Input
    abstract Property<String> getBasePackage()

    @Optional
    @InputFile
    abstract RegularFileProperty getMappings()

    @Internal
    abstract DirectoryProperty getMappingsOutput()

    @Optional
    @InputFile
    abstract RegularFileProperty getMinecraftJar()

    @Optional
    @InputDirectory
    abstract DirectoryProperty getJavaHome()

    @Optional
    @InputFiles
    abstract ConfigurableFileCollection getLibraries()

    @Optional
    @InputFiles
    abstract ConfigurableFileCollection getProguardConfigs()

    @Optional
    @Input
    abstract SetProperty<String> getExcludedArtifacts()

    @Optional
    @Input
    abstract MapProperty<String, String> getNestedBasePackages()

    @Optional
    @Input
    abstract Property<Boolean> getExcludeAllNestedJars()

    @Optional
    @Input
    abstract Property<Boolean> getOverwrite()

    @Internal
    abstract RegularFileProperty getLogOutput()

    @Internal
    abstract Property<Boolean> getDebug()

    @Input
    abstract Property<String> getToolDependency()

    @Internal
    abstract ConfigurableFileCollection getToolConfiguration()

    @Nested
    @Optional
    abstract Property<JavaLauncher> getJavaLauncher()

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService()

    @Inject
    protected abstract ExecOperations getExecOperations()

    // Internal archiveFile satellite properties -- they all feed into archiveFile
    @Internal
    abstract DirectoryProperty getDestinationDirectory()

    @Internal
    abstract Property<String> getArchiveName()

    @Internal
    abstract Property<String> getArchiveBaseName()

    @Internal
    abstract Property<String> getArchiveAppendix()

    @Internal
    abstract Property<String> getArchiveVersion()

    @Internal
    abstract Property<String> getArchiveExtension()

    @Internal
    abstract Property<String> getArchiveClassifier()

    private workingDirectory = project.layout.buildDirectory.dir(name)

    ObfuscateJar() {
        overwrite.convention(true)
        logOutput.convention(workingDirectory.map { it.file('log.txt') })
        toolDependency.convention('dev._100media:JarJarObfuscator:[1.0.9,2.0):all')
        // TODO: This doesn't actually let you change toolDependency at all -- need to find a fix to create
        //  a detached configuration that uses the project's repos, but cannot use getProject() in the task action anymore to do this.
        toolConfiguration.convention(project.configurations.detachedConfiguration(project.dependencies.create(toolDependency.get()) {
            transitive = false
        }))
        archiveClassifier.set("obf")
        archiveExtension.set(Jar.DEFAULT_EXTENSION)

        def javaExtension = project.extensions.findByType(JavaPluginExtension)
        if (javaExtension != null)
            javaLauncher.convention(javaToolchainService.launcherFor(javaExtension.getToolchain()))

        setMinimumRuntimeJavaVersion(17)

        archiveName.convention(project.provider {
            // [baseName]-[appendix]-[version]-[classifier].[extension]
            def name = GUtil.elvis(archiveBaseName.getOrNull(), "")
            // ObfuscateJar prefix required cuz closure dumbness
            name += ObfuscateJar.maybe(name, archiveAppendix.getOrNull())
            name += ObfuscateJar.maybe(name, archiveVersion.getOrNull())
            name += ObfuscateJar.maybe(name, archiveClassifier.getOrNull())

            def extension = archiveExtension.getOrNull()
            name += GUtil.isTrue(extension) ? "." + extension : ""
            return name
        })

        archiveFile.convention(destinationDirectory.file(archiveName))
    }

    void setupDefaults() {
        if (project.plugins.hasPlugin("net.minecraftforge.gradle")) {
            minecraftJar.convention(defaultMinecraftJar)
            mappings.convention(project.tasks.named('createSrgToMcp').flatMap { it.output })
        }

        // Cache in Jenkins so that we can identify potential runtime obf issues later
        mappingsOutput.convention(project.layout.buildDirectory.dir('libs'))

        if (project.properties.containsKey('mod_group_id') && project.properties['mod_group_id'] instanceof String)
            basePackage.convention(project.properties['mod_group_id'] as String)
    }

    void copyArchiveNameFrom(AbstractArchiveTask task) {
        copyArchiveNameFrom(task.name)
    }

    void copyArchiveNameFrom(String taskName) {
        copyArchiveNameFrom(project.tasks.named(taskName, AbstractArchiveTask))
    }

    /**
     * Copies all archive properties from the original task except for the classifier
     */
    void copyArchiveNameFrom(TaskProvider<? extends AbstractArchiveTask> taskProvider) {
        destinationDirectory.convention(taskProvider.flatMap { it.destinationDirectory })
        archiveBaseName.convention(taskProvider.flatMap { it.archiveBaseName })
        archiveAppendix.convention(taskProvider.flatMap { it.archiveAppendix })
        archiveVersion.convention(taskProvider.flatMap { it.archiveVersion })
        archiveExtension.convention(taskProvider.flatMap { it.archiveExtension })
    }

    void copyArchiveNameFromAndSetInput(AbstractArchiveTask task) {
        copyArchiveNameFromAndSetInput(task.name)
    }

    void copyArchiveNameFromAndSetInput(String taskName) {
        copyArchiveNameFromAndSetInput(project.tasks.named(taskName, AbstractArchiveTask))
    }

    void copyArchiveNameFromAndSetInput(TaskProvider<? extends AbstractArchiveTask> taskProvider) {
        copyArchiveNameFrom(taskProvider)
        inputFile.set(taskProvider.flatMap { it.archiveFile })
    }

    protected static String getFileLocation(Provider<FileSystemLocation> provider) {
        return provider.present ? provider.get().asFile.absolutePath : null
    }

    protected static void addIfPresent(String name, Provider<FileSystemLocation> provider, List<String> args) {
        if (provider.present) {
            args.add("--$name")
            args.add(getFileLocation(provider))
        }
    }

    @TaskAction
    void run() {
        def args = []
        args.add('--input')
        args.add(getFileLocation(inputFile))
        args.add('--output')
        args.add(getFileLocation(archiveFile))
        args.add('--base-package')
        args.add(basePackage.get())
        addIfPresent('mappings', getMappings(), args)
        addIfPresent('mappings-output', getMappingsOutput(), args)
        addIfPresent('mc-jar', getMinecraftJar(), args)
        addIfPresent('java-home', getJavaHome(), args)

        proguardConfigs.each { file ->
            args.add('--proguard-config')
            args.add(file.absolutePath)
        }
        excludedArtifacts.get().each { artifact ->
            args.add('--exclude-artifact')
            args.add(artifact)
        }
        nestedBasePackages.get().each { e ->
            args.add('--nested-base-package')
            args.add(e.key + '=' + e.value)
        }
        if (overwrite.present && overwrite.get())
            args.add('--overwrite')
        if (excludeAllNestedJars.present && excludeAllNestedJars.get())
            args.add('--exclude-all-nested')
        libraries.each { file ->
            args.add('--lib')
            args.add(file.absolutePath)
        }

        def logOutputFile = logOutput.present ? logOutput.get().asFile : null
        if (logOutputFile != null)
            logOutputFile.parentFile.mkdirs()

        try (def log = new PrintWriter(logOutput.present ? new FileWriter(logOutputFile) : NullWriter.DEFAULT, true)) {
            execOperations.javaexec {
                executable effectiveExecutable
                classpath.from toolConfiguration

                if (this.debug.present && this.debug.get())
                    setDebug(true)

                mainClass = 'dev._100media.jarjarobfuscator.Main'
                setArgs(args)

                log.println("Java Launcher: " + executable)
                log.println("Arguments: " + args.stream().collect(Collectors.joining(", ", "'", "'")))
                log.println("Classpath:")
                classpath.forEach(f -> log.println(" - " + f.getAbsolutePath()))
                log.println("Main class: " + mainClass.get())
                log.println("====================================")

                setStandardOutput(new OutputStream() {
                    @Override
                    void flush() { log.flush() }

                    @Override
                    void close() {}

                    @Override
                    void write(int b) { log.write(b) }
                })
            }.rethrowFailure().assertNormalExitValue()
        }
    }

    @Internal
    protected Provider<RegularFile> getDefaultMinecraftJar() {
        try {
            project.layout.file(project.configurations.named("minecraft").map {
                it.files[0] // This is a bit shoddy but assumes that the first file is the main MC jar
            })
        } catch (UnknownDomainObjectException e) {
            return null
        }
    }

    protected void setMinimumRuntimeJavaVersion(int version) {
        if (!javaLauncher.present || !javaLauncher.get().metadata.languageVersion.canCompileOrRun(version)) {
            setRuntimeJavaVersion(version)
        }
    }

    protected void setRuntimeJavaVersion(int version) {
        setRuntimeJavaToolchain(tc -> tc.languageVersion.set(JavaLanguageVersion.of(version)))
    }

    protected void setRuntimeJavaToolchain(JavaToolchainSpec toolchain) {
        javaLauncher.set(javaToolchainService.launcherFor(toolchain))
    }

    protected void setRuntimeJavaToolchain(Action<? super JavaToolchainSpec> action) {
        javaLauncher.set(javaToolchainService.launcherFor(action))
    }

    @Internal
    protected String getEffectiveExecutable() {
        if (javaLauncher.present) {
            return javaLauncher.get().executablePath.toString()
        } else {
            return Jvm.current().javaExecutable.absolutePath
        }
    }

    private static String maybe(@Nullable String prefix, @Nullable String value) {
        if (GUtil.isTrue(value)) {
            if (GUtil.isTrue(prefix)) {
                return "-".concat(value)
            } else {
                return value
            }
        }

        return ""
    }
}