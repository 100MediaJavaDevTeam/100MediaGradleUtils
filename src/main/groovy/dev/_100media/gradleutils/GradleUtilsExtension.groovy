package dev._100media.gradleutils

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

import javax.inject.Inject

class GradleUtilsExtension {
    private static final String PREFIX_MARKER = 'blahblahmarker'
    private final Project project

    @Inject
    GradleUtilsExtension(Project project) {
        this.project = project
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
     * Get a closure for the 100 Media maven to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    Closure get100MediaMaven() {
        return GradleUtils.get100MediaMaven(this.project)
    }

    /**
     * Get a closure for the 100 Media releases maven to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
     * in a repositories block.
     *
     * @return a closure
     */
    Closure get100MediaReleasesMaven() {
        return GradleUtils.get100MediaReleasesMaven(this.project)
    }

    /**
     * Get a closure for the 100 Media snapshots maven to be passed into {@link RepositoryHandler#maven(groovy.lang.Closure)}
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

    void addJarJarDep(dep, version, versionRange, classifier = null, prefixMcVersion = PREFIX_MARKER,
                      boolean deobf = true, implConfiguration = 'implementation', jarJarConfiguration = 'jarJar') {
        def project = this.project
        if (prefixMcVersion == PREFIX_MARKER) {
            project.afterEvaluate {
                addJarJarDep(dep, version, versionRange, classifier, project.extensions.extraProperties.find('MC_VERSION'), deobf, implConfiguration, jarJarConfiguration)
            }
            return
        }

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
            "${implConfiguration}"(deobf ? project.extensions.getByName("fg").deobf(depNotation) : depNotation)
            "${jarJarConfiguration}"("${dep}:${versionRange}${suffix}")
        }
    }
}
