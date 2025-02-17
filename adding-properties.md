# Adding properties

A sketch of what has to be done to add a typical property.

1. Define the property in `C3P0Defaults`:
   ```java
   private final static boolean CANCEL_AUTOMATICALLY_CLOSED_STATEMENTS = false;

   public static boolean cancelAutomaticallyClosedStatements()
   { return CANCEL_AUTOMATICALLY_CLOSED_STATEMENTS; }
   ```

2. Define the property in `WrapperConnectionPoolDataSourceBase.beangen-xml`:
   ```xml
   <property>
      <type>boolean</type>
      <name>cancelAutomaticallyClosedStatements</name>
      <default-value>C3P0Config.initializeBooleanPropertyVar("cancelAutomaticallyClosedStatements", C3P0Defaults.cancelAutomaticallyClosedStatements())</default-value>
      <getter><modifiers><modifier>public</modifier><modifier>synchronized</modifier></modifiers></getter>
      <setter><modifiers><modifier>public</modifier><modifier>synchronized</modifier></modifiers></setter>
   </property>
   ```

3. Define user-overridable variant on property in `WrapperConnectionPoolDataSource.java`:
   ```java
   private synchronized boolean isCancelAutomaticallyClosedStatements( String userName )
   {
      if ( userName == null )
	       return this.isCancelAutomaticallyClosedStatements();

	   Boolean override = C3P0ConfigUtils.extractBooleanUserOverride( "cancelAutomaticallyClosedStatements", userName, userOverrides );
	   return ( override == null ? this.isCancelAutomaticallyClosedStatements() : override.booleanValue() );
   }
   ```

4. Add the property to `AbstractComboPooledDataSource.java`:
   ```java
   public boolean isCancelAutomaticallyClosedStatements()
   { return wcpds.isCancelAutomaticallyClosedStatements(); }

   public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements )
   { 
       if ( diff(wcpds.isCancelAutomaticallyClosedStatements(), cancelAutomaticallyClosedStatements) )
       {
           wcpds.setCancelAutomaticallyClosedStatements( cancelAutomaticallyClosedStatements ); 
           this.resetPoolManager( false );
       }
   }
   ```

5. Add the property to `JndiRefConnectionPoolDataSource.java`:
   ```java
   public boolean isCancelAutomaticallyClosedStatements()
   { return wcpds.isCancelAutomaticallyClosedStatements(); }

   public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements )
   { wcpds.setCancelAutomaticallyClosedStatements( cancelAutomaticallyClosedStatements ); }
    
   // don't forget to include the new property in ReferenceMaker!
   referenceMaker.addReferenceProperty("cancelAutomaticallyClosedStatements");
   ```
   
6. Add the property to `jboss/C3P0PooledDataSource.java`:
   ```java
   public boolean isCancelAutomaticallyClosedStatements()
   { return combods.isCancelAutomaticallyClosedStatements(); }

   public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements ) throws NamingException
   {
     combods.setCancelAutomaticallyClosedStatements( cancelAutomaticallyClosedStatements );
     rebind();
   }
   ```

6. Add the property to `jboss/C3P0PooledDataSource.java`:
   ```java
   public boolean isCancelAutomaticallyClosedStatements()
   { return combods.isCancelAutomaticallyClosedStatements(); }

   public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements ) throws NamingException
   {
     combods.setCancelAutomaticallyClosedStatements( cancelAutomaticallyClosedStatements );
     rebind();
   }
   ```

7. Add the property to `jboss/C3P0PooledDataSourceMBean.java`:
   ```java
   public boolean isCancelAutomaticallyClosedStatements();
   public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements ) throws NamingException;
   ```

8. Add the property to `mbean/C3P0PooledDataSource.java`:
   ```java
   public boolean isCancelAutomaticallyClosedStatements()
   { return combods.isCancelAutomaticallyClosedStatements(); }

   public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements ) throws NamingException
   {
     combods.setCancelAutomaticallyClosedStatements( cancelAutomaticallyClosedStatements );
     rebind();
   }
   ```

9. Add the property to `mbean/C3P0PooledDataSourceMBean.java`:
   ```java
   public boolean isCancelAutomaticallyClosedStatements();

   public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements ) throws NamingException;
   ```

10. Implement parameter-specific logic.

11. Document parameter in `index.html`:
    ```html
    ```

12. [optional] Add parameter to `example/c3p0-service.xml`
    ```xml
    <!-- <attribute name="CancelAutomaticallyClosedStatements">false</attribute> -->
    ```

13. [optional] Add parameter to `test/resources-local/c3p0.properties` and `test/resources-local-rough/c3p0.properties`
