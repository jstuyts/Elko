package org.elkoserver.develop.gradle.apiclasses

import javassist.ClassPool
import javassist.CtClass
import javassist.Modifier
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.compile.AbstractCompile
import java.io.File

// TODO: Report classes that must have an implementation dependency. Rename this class and the existing tasks that use it
// TODO: Report (project and external) modules instead of classes, so it is much easier to update the dependencies. Even better: output the dependency declarations
open class ApiClassesTask : DefaultTask() {
    init {
        dependsOn += project.tasks.getByPath("classes")

        doLast {
            val transitiveDependencies = mutableSetOf<Task>()
            addDependenciesOf(this, transitiveDependencies)
            val jvmCompileTasksOfProject = transitiveDependencies.filterIsInstance<AbstractCompile>().filter { it.project == project }

            val fullClasspath = mutableListOf<File>()
            jvmCompileTasksOfProject.forEach { task ->
                fullClasspath.addAll(task.classpath.toList())
                fullClasspath.add(task.destinationDirectory.asFile.get())
            }

            val projectClassNames = jvmCompileTasksOfProject.flatMap { task ->
                val taskDestinationDirectory = task.destinationDirectory.asFile.get()
                taskDestinationDirectory.walk().filter { file ->
                    file.isFile && file.name.endsWith(".class")
                }.map { file ->
                    file.relativeTo(taskDestinationDirectory).path.removeSuffix(".class").replace('\\', '.')
                }.toSet()
            }

            outputClasses(fullClasspath, projectClassNames)
        }
    }

    private fun addDependenciesOf(task: Task, transitiveDependencies: MutableSet<Task>) {
        project.gradle.taskGraph.getDependencies(task).forEach {
            if (!transitiveDependencies.contains(it)) {
                transitiveDependencies.add(it)
                addDependenciesOf(it, transitiveDependencies)
            }
        }
    }

    private fun outputClasses(projectPlusImplementationClasspath: MutableList<File>, projectClassNames: Collection<String>) {
        val classPool = ClassPool().apply {
            appendSystemPath()
            projectPlusImplementationClasspath.forEach { file ->
                appendClassPath(file.path)
            }
        }

        val processedClasses = mutableSetOf<String>()
        val apiClassNames = sortedSetOf<String>()
        fun addIfNotProjectClass(foundClassName: String) {
            if (!projectClassNames.contains(foundClassName)) {
                apiClassNames.add(foundClassName)
            }
        }
        projectClassNames.forEach { className ->
            val ctClass = classPool.get(className)
            handleClassIfPublic(ctClass, projectClassNames, processedClasses, classPool, ::addIfNotProjectClass)
        }
        println(apiClassNames.joinToString(System.lineSeparator()))
    }

    private fun handleClassesIfPublic(ctClasses: Array<CtClass>, projectClassNames: Collection<String>, processedClasses: MutableCollection<String>, classPool: ClassPool, classNameCollector: (String) -> Unit) {
        ctClasses.forEach { handleClassIfPublic(it, projectClassNames, processedClasses, classPool, classNameCollector) }
    }

    private fun handleClassIfPublic(ctClass: CtClass, projectClassNames: Collection<String>, processedClasses: MutableCollection<String>, classPool: ClassPool, classNameCollector: (String) -> Unit) {
        if (!processedClasses.contains(ctClass.name)) {
            processedClasses.add(ctClass.name)
            var classToReport: CtClass
            if (ctClass.isArray) {
                classToReport = ctClass
                while (classToReport.isArray) {
                    classToReport = classToReport.componentType
                }
            } else {
                classToReport = ctClass
            }
            if (!classToReport.isPrimitive && !projectClassNames.contains(classToReport.name)
                    && !classToReport.name.startsWith("java.")
                    && !classToReport.name.startsWith("javax.")
                    && !classToReport.name.startsWith("com.sun.")
                    && !classToReport.name.startsWith("kotlin.")
                    && !classToReport.name.startsWith("kotlinx.")
                    && !classToReport.name.startsWith("org.jetbrains.annotations.")) {
                classNameCollector(classToReport.name)
            }
            if (projectClassNames.contains(ctClass.name)) {
                if (ctClass.modifiers.and(PUBLIC) != 0) {
                    handleClass(ctClass, projectClassNames, processedClasses, classPool, classNameCollector)
                }
            }
        }
    }

    // Only invoked for annotation method return types.
    private fun handleClassIfPublic(clazz: Class<*>, projectClassNames: Collection<String>, processedClasses: MutableCollection<String>, classPool: ClassPool, classNameCollector: (String) -> Unit) {
        if (!processedClasses.contains(clazz.name)) {
            processedClasses.add(clazz.name)
            var classToReport: Class<*>
            if (clazz.isArray) {
                classToReport = clazz
                while (classToReport.isArray) {
                    classToReport = classToReport.componentType
                }
            } else {
                classToReport = clazz
            }
            if (!classToReport.isPrimitive && !projectClassNames.contains(classToReport.name)
                    && !classToReport.name.startsWith("java.")
                    && !classToReport.name.startsWith("javax.")
                    && !classToReport.name.startsWith("com.sun.")
                    && !classToReport.name.startsWith("kotlin.")
                    && !classToReport.name.startsWith("kotlinx.")
                    && !classToReport.name.startsWith("org.jetbrains.annotations.")) {
                classNameCollector(classToReport.name)
            }
            if (projectClassNames.contains(clazz.name)) {
                if (clazz.modifiers.and(PUBLIC) != 0) {
                    handleClass(classPool.get(clazz.name), projectClassNames, processedClasses, classPool, classNameCollector)
                }
            }
        }
    }

    private fun handleClass(ctClass: CtClass, projectClassNames: Collection<String>, processedClasses: MutableCollection<String>, classPool: ClassPool, classNameCollector: (String) -> Unit) {
        handleClassIfPublic(ctClass.superclass, projectClassNames, processedClasses, classPool, classNameCollector)
        handleClassesIfPublic(ctClass.interfaces, projectClassNames, processedClasses, classPool, classNameCollector)
        handleAnnotations(ctClass.annotations, projectClassNames, processedClasses, classPool, classNameCollector)
        ctClass.declaredConstructors.forEach { ctConstructor ->
            if (ctConstructor.modifiers.and(PUBLIC_OR_PROTECTED) != 0) {
                handleAnnotations(ctConstructor.annotations, projectClassNames, processedClasses, classPool, classNameCollector)
                ctConstructor.parameterAnnotations.forEach { handleAnnotations(it, projectClassNames, processedClasses, classPool, classNameCollector) }
                handleClassesIfPublic(ctConstructor.exceptionTypes, projectClassNames, processedClasses, classPool, classNameCollector)
                handleClassesIfPublic(ctConstructor.parameterTypes, projectClassNames, processedClasses, classPool, classNameCollector)
            }
        }
        ctClass.declaredFields.forEach { ctField ->
            if (ctField.modifiers.and(PUBLIC_OR_PROTECTED) != 0) {
                handleAnnotations(ctField.annotations, projectClassNames, processedClasses, classPool, classNameCollector)
                handleClassIfPublic(ctField.type, projectClassNames, processedClasses, classPool, classNameCollector)
            }
        }
        ctClass.declaredMethods.forEach { ctMethod ->
            if (ctMethod.modifiers.and(PUBLIC_OR_PROTECTED) != 0) {
                handleAnnotations(ctMethod.annotations, projectClassNames, processedClasses, classPool, classNameCollector)
                ctMethod.parameterAnnotations.forEach { handleAnnotations(it, projectClassNames, processedClasses, classPool, classNameCollector) }
                handleClassesIfPublic(ctMethod.parameterTypes, projectClassNames, processedClasses, classPool, classNameCollector)
                handleClassesIfPublic(ctMethod.exceptionTypes, projectClassNames, processedClasses, classPool, classNameCollector)
                handleClassIfPublic(ctMethod.returnType, projectClassNames, processedClasses, classPool, classNameCollector)
            }
        }
    }

    private fun handleAnnotations(annotations: Array<Any>, projectClassNames: Collection<String>, processedClasses: MutableCollection<String>, classPool: ClassPool, classNameCollector: (String) -> Unit) {
        annotations.forEach { annotation ->
            val annotationInterface = annotation.javaClass.interfaces[0]
            if (!annotationInterface.name.startsWith("java.")
                    && !annotationInterface.name.startsWith("javax.")
                    && !annotationInterface.name.startsWith("com.sun.")
                    && !annotationInterface.name.startsWith("kotlin.")
                    && !annotationInterface.name.startsWith("kotlinx.")
                    && !annotationInterface.name.startsWith("org.jetbrains.annotations.")) {
                classNameCollector(annotationInterface.name)
            }
            annotationInterface.declaredMethods.forEach { method ->
                handleClassIfPublic(method.returnType, projectClassNames, processedClasses, classPool, classNameCollector)
            }
        }
    }

    companion object {
        private const val PUBLIC = Modifier.PUBLIC
        private const val PUBLIC_OR_PROTECTED = Modifier.PUBLIC.or(Modifier.PROTECTED)
    }
}
