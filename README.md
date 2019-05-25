# frixum-pullum
### How to import project into Android studios
1. Copy SSH-key
2. In android studios: File -> New -> Project from Version Control -> Git
3. Paste SSH-key in Git Repository URL, choose parent directory and name.
4. Clone.

### Fixes for errores that can occur when trying to run
1. Cannot load module
 
    ![missing module](/READMEimgs/Selection_054.png)

    Press details... and choose to remove the module.

2. Install NDK
   1. Goto Tools -> Android -> SDK Manager.
   2. Choose the tab SDK Tools.
   3. Check NDK and press apply.

3. error: package R does not exist
   1. Double click the error message
   2. When you see this message, press Alt + enter.
   ![resolve R](/READMEimgs/Selection_056.png)

4. Class not found using the boot class loader; no stack available
   (Very long error message)
   
   Change the name of the package in AndroidManifest.xml, anctivity_main.xml, MainActivity.java.
   to match the package name.

   Change: se.nugify.frixumpullum.frixumpullum
   to: se.nugify.frixumpullum.app
   
   ![package name](/READMEimgs/Selection_057.png)
   
5. cannot resolve corresponding jni function
   
   No known fix...yet.