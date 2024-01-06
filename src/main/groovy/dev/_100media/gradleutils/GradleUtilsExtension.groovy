package dev._100media.gradleutils

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.RepositoryHandler

import javax.inject.Inject

class GradleUtilsExtension {
    private static final String PREFIX_MARKER = 'blahblahmarker'
    private final Project project
    private String mcVersion
    private final Set<Dependency> excludedDependencies = new HashSet<>()

    @Inject
    GradleUtilsExtension(Project project) {
        this.project = project
    }

    void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion
    }

    void mcVersion(String mcVersion) {
        this.setMcVersion(mcVersion)
    }

    /**
     * Get a closure to be passed into {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)}
     * in a publishing block.
     * <p>
     * The following environment variables must be set for this to work:
     * <ul>
     *  <li>MAVEN_USER: Contains the username to use for authentication</li>
     *  <li>MAVEN_PASSWORD: Contains the password to use for authentication</li>
     * </ul>
     * The following environment variables are optional:
     * <ul>
     *  <li>MAVEN_URL_RELEASES: Contains the URL to use for the release repository</li>
     *  <li>MAVEN_URL_SNAPSHOTS: Contains the URL to use for the snapshot repository</li>
     * </ul>
     *
     * @param defaultReleasesFolder the default releases folder if the required maven information is not currently set
     * @param defaultSnapshotsFolder the default snapshots folder if the required maven information is not currently set
     * @return a closure
     */
    Closure getPublishing100MediaMaven(File defaultReleasesFolder = this.project.rootProject.file('repo'), File defaultSnapshotsFolder = this.project.rootProject.file('repo')) {
        return GradleUtils.getPublishing100MediaMaven(this.project, defaultReleasesFolder, defaultSnapshotsFolder)
    }

    /**
     * Get a closure for the 100 Media maven including credentials to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    Closure get100MediaMaven() {
        return GradleUtils.get100MediaMaven(this.project)
    }

    /**
     * Get a closure for the 100 Media public maven to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    Closure get100MediaPublicMaven() {
        return GradleUtils.get100MediaPublicMaven(this.project)
    }

    /**
     * Get a closure for the 100 Media releases maven including credentials to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    Closure get100MediaReleasesMaven() {
        return GradleUtils.get100MediaReleasesMaven(this.project)
    }

    /**
     * Get a closure for the 100 Media snapshots maven including credentials to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    Closure get100MediaSnapshotsMaven() {
        return GradleUtils.get100MediaSnapshotsMaven(this.project)
    }

    void addJarJarDepNoRangePrefix(dep, version, versionRange, classifier = null, boolean deobf = true,
                                   implConfiguration = 'implementation', jarJarConfiguration = 'jarJar') {
        addJarJarDep(dep, version, versionRange, classifier, null, deobf, implConfiguration, jarJarConfiguration)
    }

    void addJarJarDep(dep, version, versionRange, Map<String, ?> map) {
        addJarJarDep([dep: dep, version: version, versionRange: versionRange] + map)
    }

    void addJarJarDep(Map<String, ?> map) {
        if (map.excludeFromObf) {
            map = new HashMap<>(map)
            map['configureJarJarDep'] = { Dependency dep ->
                excludeFromObf(dep)
            }
        }
        addJarJarDep(map.dep, map.version, map.versionRange, map.classifier,
                map.containsKey('prefixMcVersion') ? map.prefixMcVersion : this.mcVersion,
                map.containsKey('deobf') ? map.deobf : true,
                map.containsKey('implConfiguration') ? map.implConfiguration : 'implementation',
                map.containsKey('jarJarConfiguration') ? map.jarJarConfiguration : 'jarJar',
                map.configureImplDep, map.configureJarJarDep)
    }

    void addJarJarDep(dep, version, versionRange, CharSequence classifier = null, prefixMcVersion = this.mcVersion,
                      boolean deobf = true, implConfiguration = 'implementation', jarJarConfiguration = 'jarJar',
                      Action<? super Dependency> configureImplDep = null, Action<? super Dependency> configureJarJarDep = null) {
        def project = this.project
        def suffix = classifier != null && !classifier.isEmpty() ? ":${classifier}" : ""
        if (prefixMcVersion != null && !prefixMcVersion.isEmpty()) {
            def commaIdx = versionRange.indexOf(',')
            def length = versionRange.length()
            if (commaIdx == -1)
                commaIdx = length
            def temp = "${versionRange.charAt(0)}" + prefixMcVersion + '-' + versionRange.substring(1, commaIdx)
            if (commaIdx != length)
                temp = temp + ',' + prefixMcVersion + '-' + versionRange.substring(commaIdx + 1)
            versionRange = temp
        }

        project.dependencies {
            def depNotation = "${dep}:${version}"
            "${implConfiguration}"(deobf ? project.extensions.getByName("fg").deobf(depNotation) : depNotation) {
                if (configureImplDep != null)
                    configureImplDep.execute(it)
            }
            "${jarJarConfiguration}"("${dep}:${versionRange}${suffix}") {
                if (configureJarJarDep != null)
                    configureJarJarDep.execute(it)
            }
        }
    }

    void excludeFromObf(Dependency dependency) {
        // Closure BS
        def excludedDependencies = excludedDependencies
        if (excludedDependencies.isEmpty()) {
            this.project.afterEvaluate { Project project ->
                try {
                    project.tasks.named('obfuscateJar', ObfuscateJar) {
                        excludedArtifacts.addAll(excludedDependencies.collect { dep ->
                            "${dep.group}:${dep.name}"
                        })
                    }
                } catch (UnknownTaskException ignored) {
                    project.logger.warn('Attempted to exclude dependencies from obfuscation but could not find a task named \'obfuscateJar\'')
                }
            }
        }

        excludedDependencies.add(dependency)
    }

    void configureObfuscateJar(Action<? super ObfuscateJar> action) {
        this.project.tasks.named('assemble') {
            dependsOn 'obfuscateJar'
        }

        this.project.tasks.register('obfuscateJar', ObfuscateJar, action)
    }
}
