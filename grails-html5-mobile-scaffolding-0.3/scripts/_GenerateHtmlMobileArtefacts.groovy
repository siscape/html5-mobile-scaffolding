/** Copyright 2012 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

/**
*
* @author <a href='mailto:th33musk3t33rs@gmail.com'>3.musket33rs</a>
*
* @since 0.1
*/

import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.Assert
import org.codehaus.groovy.grails.scaffolding.*
import grails.persistence.Event
import groovy.io.FileType

includeTargets << grailsScript("_GrailsCreateArtifacts")
includeTargets << grailsScript("_GrailsGenerate")
includeTargets << grailsScript("_GrailsBootstrap")
includeTargets << grailsScript('_GrailsPackage')

// default values
generateForName = null
htmlViewName = null
generateViews = true
generateController = true

target(generateForOne: 'Generates controllers and views for only one domain class.') {
  depends compile, loadApp
  
  //println "BEFORE"
  //def myClass = classLoader.loadClass('org.grails.html.mobile.HtmlMobileTemplateGenerator')
  //println "AFTER" + myClass
  
  def name = generateForName
  def viewName = htmlViewName
  

  name = name.indexOf('.') > 0 ? name : GrailsNameUtils.getClassNameRepresentation(name)

  def domainClass = grailsApp.getDomainClass(name)
  //grailsConsole.updateStatus "domain $domainClass "

  if (!domainClass) {
    grailsConsole.updateStatus 'Domain class not found in grails-app/domain, trying hibernate mapped classes...'
    bootstrap()
    domainClass = grailsApp.getDomainClass(name)
  }
  
  //grailsConsole.updateStatus "Generating HTML view ${viewName} for ${name}"
  
  if (domainClass) {
    generateForDomainClass(domainClass, viewName)
    event 'StatusFinal', [ "Finished generation for domain class ${domainClass.fullName}" ]
  }
  else {
    event 'StatusFinal', [
      "No domain class found for name ${name}. Please try again and enter a valid domain class name"
    ]
    exit 1
  }
}
HtmlMobileTemplateGenerator initHtmlMobileTemplate(viewName) {
  def templateGenerator = new HtmlMobileTemplateGenerator(classLoader, viewName)
  templateGenerator.grailsApplication = grailsApp
  templateGenerator.pluginManager = pluginManager
  templateGenerator.event = event
  templateGenerator
}

def generateForDomainClass(domainClass, viewName) {
  def templateGenerator = initHtmlMobileTemplate viewName
  
  if (generateViews) {
    event 'StatusUpdate', ["Generating views for domain class ${domainClass.fullName}"]



      templateGenerator.generateViews(domainClass, basedir)
    event 'GenerateViewsEnd', [domainClass.fullName]
  }

  if (generateController) {
    event 'StatusUpdate', ["Generating controller for domain class ${domainClass.fullName}"]
    templateGenerator.generateController(domainClass, basedir)
    event 'GenerateControllerEnd', [domainClass.fullName]
  }
}

class HtmlMobileTemplateGenerator extends DefaultGrailsTemplateGenerator {

 def event
 def viewName
 
 HtmlMobileTemplateGenerator(ClassLoader classLoader, String viewName) {
   super(classLoader)
   this.viewName = viewName
 }

 @Override
 void generateViews(GrailsDomainClass domainClass, String destdir) {
   Assert.hasText destdir, 'Argument [destdir] not specified'

   for (t in getTemplateNames()) {
     //event 'StatusUpdate', ["Generating $t for domain class ${domainClass.fullName}"]
     generateView domainClass, t, new File(destdir).absolutePath
   }
 }

 void copyGrailsMobileFrameworkIfNotPresent(String base) {
    def source = "$base/src/templates/scaffolding/grails"
    def destination = "$base/web-app/js/grails"
    if (!new File(destination).exists()) {
      new AntBuilder().copy( todir:destination ) {
        fileset( dir: source )
      }
    }
 }

 @Override
 void generateView(GrailsDomainClass domainClass, String templateViewName, Writer out) {
   def templateText = getTemplateText(templateViewName)
   if (templateText) {

     def t = engine.createTemplate(templateText)
     def multiPart = domainClass.properties.find {it.type == ([] as Byte[]).class || it.type == ([] as byte[]).class}

     boolean hasHibernate = pluginManager?.hasGrailsPlugin('hibernate')
     def packageName = domainClass.packageName
     def project = this.grailsApplication.metadata['app.name']
     
     def excludedProps = Event.allEvents.toList() << 'id' << 'version' << 'longitude' << 'latitude'
     def allowedNames = domainClass.persistentProperties*.name << 'dateCreated' << 'lastUpdated'
     def props = domainClass.properties.findAll { allowedNames.contains(it.name) && !excludedProps.contains(it.name) && it.type != null && !Collection.isAssignableFrom(it.type) }
     
     def listProps = domainClass.properties.findAll {Collection.isAssignableFrom(it.type)}
     
     def oneToOneProps = props.findAll { it.isOneToOne() }
     
     def latitude = domainClass.properties.find { it.name == "latitude" }
     def longitude = domainClass.properties.find { it.name == "longitude" }
     
     boolean geolocated = latitude && longitude
    
     Closure resourceClosure = domainClass.getStaticPropertyValue('mapping', Closure)
     def geoProps = [:]
     if (resourceClosure) {
       def myMap = [:]
       def populator = new grails.util.ClosureToMapPopulator(myMap)
       populator.populate resourceClosure
       
       geoProps = myMap.findAll { listProps*.name.contains(it?.key) && it?.value?.geoIndex}
     }
     def binding = [pluginManager: pluginManager,
       project: project,
       packageName: packageName,
       domainClass: domainClass,
       props: props,
       oneToOneProps: oneToOneProps,
       geolocated: geolocated,
       geoProps:geoProps,
       className: domainClass.shortName]

     t.make(binding).writeTo(out)
   }
 }

 @Override
 void generateView(GrailsDomainClass domainClass, String templateViewName, String destDir) {
     println "------------- generate view start " + templateViewName + domainClass.packageName
   def suffix = templateViewName.find(/\.\w+$/)

   def viewsDir
   def destFile
   if (suffix == '.html') {
     viewsDir = new File("$destDir/web-app")
   } else if (suffix == '.js') {
       if (templateViewName.startsWith("view")) {
         viewsDir = new File("$destDir/web-app/js/" + domainClass.packageName + "/view")
       } else {
           viewsDir = new File("$destDir/web-app/js/" + domainClass.packageName)
       }
       copyGrailsMobileFrameworkIfNotPresent(destDir)
   }
     println "------------- views dir " + viewsDir.path

     if (!viewsDir.exists()) viewsDir.mkdirs()
  
   if (suffix == '.html') { // for html files
     if (viewName) { // either 2nd paramater of hGV command
       destFile = new File(viewsDir, viewName)
     } else if (domainClass.name == grailsApplication.config?.grails?.scaffolding?.html?.mobile?.index) { // or configured in Config 
       destFile = new File(viewsDir, "${templateViewName.toLowerCase()}")
     } else { //by default by convention className-index.html
       destFile = new File(viewsDir, "${domainClass.propertyName.toLowerCase()}-${templateViewName.toLowerCase()}")
     }
   } else { // for js
     destFile = new File(viewsDir, "${domainClass.propertyName.toLowerCase()}-${templateViewName.toLowerCase()}")
   }

   destFile.withWriter { Writer writer ->
     generateView domainClass, templateViewName, writer
   }
     println "------------- generate view finish"
 }

 @Override
 def getTemplateNames() {
   def resources = []
   def resolver = new PathMatchingResourcePatternResolver()
   def templatesDirPath = "${basedir}/src/templates/scaffolding"
   def templatesDir = new FileSystemResource(templatesDirPath)
   if (templatesDir.exists()) {
     try {
       resources.addAll(resolver.getResources("file:$templatesDirPath/*.html").filename)
       resources.addAll(resolver.getResources("file:$templatesDirPath/*.js").filename)
     } catch (e) {
       event 'StatusError', ['Error while loading views from grails-app scaffolding folder', e]
     }
   }
   resources
 }

 private String getPropertyName(GrailsDomainClass domainClass) { "${domainClass.propertyName}${domainSuffix}" }

}
