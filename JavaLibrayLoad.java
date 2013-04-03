    // carrega biblioteca jogl
  	System.setProperty( "java.library.path", "C:/Users/murilo.ferreira/Desktop/jogl/i586" );
		Field fieldSysPath = ClassLoader.class.getDeclaredField( "sys_paths" );
		fieldSysPath.setAccessible( true );
		fieldSysPath.set( null, null );
