private void loadParameterGroupsByClassPath() throws Exception {
        // Carregamos todos os descritores de parametros encontrados no classpath.
        Enumeration urls = Thread.currentThread().getContextClassLoader().getResources("META-INF/param-cfg.xml");
        
  	for (; urls.hasMoreElements();) {
			URL url = (URL) urls.nextElement();
			log("Lendo descritor de '" + url + "'");
            SAXBuilder saxBuilder = new SAXBuilder();
            Document   japeCfg = saxBuilder.build(url.openStream());
            List       groupList = japeCfg.getRootElement().getChildren("group");

			for (Iterator ite = groupList.iterator(); ite.hasNext();) {
				loadParameterGroup((Element) ite.next());
            }
        }
    }
