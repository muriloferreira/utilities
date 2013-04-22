package br.com.sankhya.modelcore.comercial;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.fileupload.FileItem;
import org.jdom.Element;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.PersistenceException;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.jape.vo.PrePersistEntityState;
import br.com.sankhya.modelcore.comercial.ParsedXmlNotaCompra.DiversosXmlCompra;
import br.com.sankhya.modelcore.comercial.ParsedXmlNotaCompra.EmpresaXmlCompra;
import br.com.sankhya.modelcore.comercial.ParsedXmlNotaCompra.FinanceiroXmlCompra;
import br.com.sankhya.modelcore.comercial.ParsedXmlNotaCompra.FornecedorXmlCompra;
import br.com.sankhya.modelcore.comercial.ParsedXmlNotaCompra.ItemXmlCompra;
import br.com.sankhya.modelcore.comercial.ParsedXmlNotaCompra.LoteXmlCompra;
import br.com.sankhya.modelcore.comercial.ParsedXmlNotaCompra.TransportadoraXmlCompra;
import br.com.sankhya.modelcore.comercial.centrais.CACHelper;
import br.com.sankhya.modelcore.comercial.util.LoteInfoUtil.EstoqueLoteInfo;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.ListenerParameters;
import br.com.sankhya.ws.ServiceContext;

import com.sankhya.util.BigDecimalUtil;
import com.sankhya.util.StringUtils;
import com.sankhya.util.XMLUtils;

public class ImportadorXmlNotaCompraHelper {
  private EntityFacade			dwfEntityFacade;
	private DynamicVO				cabVo;
	private Map<String, String>		prodsCadastrados;
	private Map<String, BigDecimal>	valoresLote;
	private BigDecimal				nuNotaOrig;

	private final MathContext		MATH_CTX	= new MathContext(16, RoundingMode.HALF_UP);

	public ImportadorXmlNotaCompraHelper() throws Exception {
		dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
	}

	public void setProdsCadastrados(List<Element> prods) {
		prodsCadastrados = new HashMap<String, String>();

		for (Element element : prods) {
			prodsCadastrados.put(XMLUtils.getAttributeAsString(element, "CODPRODNFE"), XMLUtils.getAttributeAsString(element, "CODPROD") + "-" +XMLUtils.getAttributeAsString(element, "CODVOL"));
		}
	}

	public Element importaXml(BigDecimal nuNota, Object fileItem) throws Exception {

		ParsedXmlNotaCompra xmlNotaCompra = new ParsedXmlNotaCompra((FileItem) fileItem);
		xmlNotaCompra.parsearXML();

		PersistentLocalEntity persistentCabecalho = (PersistentLocalEntity) dwfEntityFacade.findEntityByPrimaryKey(DynamicEntityNames.CABECALHO_NOTA, nuNota);
		cabVo = (DynamicVO) persistentCabecalho.getValueObject();

		validaVariosPedidos();
		validaChaveNfe(xmlNotaCompra);
		validaEmpresa(xmlNotaCompra.getEmpresa());
		validaFornecedor(xmlNotaCompra.getFornecedor());
		validaTransportadora(xmlNotaCompra.getTransportadora());

		xmlNotaCompra.parseItens();
		xmlNotaCompra.parseFinanceiro();

		Collection<Element> itensSemProd = validaItens(xmlNotaCompra.getItens());
		if (!itensSemProd.isEmpty()) {
			Element itensSemProdutoElement = new Element("mensagens");
			XMLUtils.addAttributeElement(itensSemProdutoElement, "mensagem", "Código de produto ou Código da unidade não encontrados para alguns itens.");
			itensSemProdutoElement.addContent(itensSemProd);

			return itensSemProdutoElement;
		}

		validaDiversos(xmlNotaCompra.getDiversos());

		persistentCabecalho.setValueObject((EntityVO) cabVo);

		gravar(xmlNotaCompra);

		return new Element("nada");
	}

	private void validaVariosPedidos() throws Exception {

		Collection<DynamicVO> itensVO = dwfEntityFacade.findByDynamicFinderAsVO(new FinderWrapper(DynamicEntityNames.ITEM_NOTA, "this.NUNOTA = ?", new Object[] { cabVo.asBigDecimal("NUNOTA") }));

		if (!itensVO.isEmpty()) {
			NativeSql sql = new NativeSql(dwfEntityFacade.getJdbcWrapper());
			sql.appendSql(" SELECT DISTINCT NUNOTAORIG ");
			sql.appendSql(" FROM TGFVAR ");
			sql.appendSql(" WHERE NUNOTA = :NUNOTA ");
			sql.appendSql(" AND NUNOTA <> NUNOTAORIG ");

			sql.setNamedParameter("NUNOTA", cabVo.asBigDecimal("NUNOTA"));

			ResultSet rset = sql.executeQuery();

			if (rset.next()) {
				nuNotaOrig = rset.getBigDecimal("NUNOTAORIG");
			}

			if (rset.next()) {
				throw new PersistenceException("Não é permitido importar XML em nota originada de mais de um pedido.");
			}
		}else{
			nuNotaOrig = BigDecimal.ZERO;
		}
	}

	private void validaDiversos(DiversosXmlCompra diversosXmlNotaCompra) {
		cabVo.setProperty("DTNEG", diversosXmlNotaCompra.getDtNeg());
		cabVo.setProperty("NUMNOTA", diversosXmlNotaCompra.getNumNota());
		cabVo.setProperty("SERIENOTA", diversosXmlNotaCompra.getSerieNota());
		cabVo.setProperty("NUMALEATORIO", diversosXmlNotaCompra.getNumAleatorio());
		cabVo.setProperty("OBSERVACAO", diversosXmlNotaCompra.getObservacao());
		cabVo.setProperty("VLRFRETE", diversosXmlNotaCompra.getVlrFrete());
		cabVo.setProperty("VLRSEG", diversosXmlNotaCompra.getVlrSeg());
		cabVo.setProperty("VLRIPI", diversosXmlNotaCompra.getVlrIpi());
		cabVo.setProperty("VLRDESTAQUE", diversosXmlNotaCompra.getVlrDestaque());
	}

	private Collection<Element> validaItens(Collection<ItemXmlCompra> itensXmlCompra) throws Exception {

		Collection<Element> itensSemProduto = new ArrayList<Element>();
		
		for (ItemXmlCompra itemXmlCompra : itensXmlCompra) {

			Collection<DynamicVO> parcsProdsVO = dwfEntityFacade.findByDynamicFinderAsVO(new FinderWrapper("RelacionamentoParceiroProduto", "this.CODPARC = ? AND this.CODPROPARC = ?", new Object[] { cabVo.asBigDecimal("CODPARC"), itemXmlCompra.getProdutoNfe() }));

			if (!parcsProdsVO.isEmpty()) {
				DynamicVO parcProdVO = parcsProdsVO.iterator().next();

				itemXmlCompra.setCodProd(parcProdVO.asBigDecimalOrZero("CODPROD"));

				if (!parcProdVO.asBoolean("Produto.USALOCAL")) {
					itemXmlCompra.setLocalPadrao(BigDecimal.ZERO);
				}
			}
			
			if (BigDecimalUtil.getValueOrZero(itemXmlCompra.getCodProd()).intValue() == 0) {
				NativeSql sql = new NativeSql(dwfEntityFacade.getJdbcWrapper());

				sql.appendSql(" SELECT CODPROD, USALOCAL ");
				sql.appendSql(" 	FROM TGFPRO ");
				sql.appendSql(" 	WHERE REFERENCIA = :REFERENCIA ");
				if(StringUtils.getEmptyAsNull(itemXmlCompra.getGtinNfe()) == null){
					sql.appendSql(" 	OR REFERENCIA IS NULL ");
				}
				sql.appendSql(" 		AND (:NUNOTAORIG = 0 OR CODPROD IN (SELECT DISTINCT CODPROD FROM TGFITE WHERE NUNOTA = :NUNOTAORIG)) ");
				sql.appendSql(" UNION ");
				sql.appendSql(" SELECT CODPROD, USALOCAL ");
				sql.appendSql(" 	FROM TGFPRO ");
				sql.appendSql(" 	WHERE REFFORN = :REFFORN ");
				sql.appendSql(" 		 AND NOT EXISTS(SELECT 1 FROM TGFPRO WHERE REFERENCIA = :REFERENCIA ");
				if(StringUtils.getEmptyAsNull(itemXmlCompra.getGtinNfe()) == null){
					sql.appendSql(" 	OR REFERENCIA IS NULL ");
				}
				sql.appendSql(" ) ");
				sql.appendSql(" 		 AND (:NUNOTAORIG = 0 OR CODPROD IN (SELECT DISTINCT CODPROD FROM TGFITE WHERE NUNOTA = :NUNOTAORIG)) ");
				sql.appendSql(" UNION ");
				sql.appendSql(" SELECT CODPROD, USALOCAL ");
				sql.appendSql(" 	FROM TGFPRO ");
				sql.appendSql(" 	WHERE DESCRPROD LIKE :DESCRPROD ");
				sql.appendSql(" 		AND NOT EXISTS(SELECT 1 FROM TGFPRO WHERE REFERENCIA = :REFERENCIA");
				if(StringUtils.getEmptyAsNull(itemXmlCompra.getGtinNfe()) == null){
					sql.appendSql(" 	OR REFERENCIA IS NULL ");
				}
				sql.appendSql(" ) ");
				sql.appendSql(" 		AND NOT EXISTS(SELECT 1 FROM TGFPRO WHERE REFFORN = :REFFORN) ");
				sql.appendSql(" 		AND (:NUNOTAORIG = 0 OR CODPROD IN (SELECT DISTINCT CODPROD FROM TGFITE WHERE NUNOTA = :NUNOTAORIG)) ");

				sql.setNamedParameter("REFFORN", itemXmlCompra.getProdutoNfe());
				sql.setNamedParameter("REFERENCIA", itemXmlCompra.getGtinNfe());
				sql.setNamedParameter("DESCRPROD", "%" + itemXmlCompra.getProduto() + "%");
				sql.setNamedParameter("NUNOTAORIG", BigDecimal.ZERO);

				ResultSet rset = sql.executeQuery();

				if (rset.next()) {
					itemXmlCompra.setCodProd(rset.getBigDecimal("CODPROD"));

					if ("N".equals(rset.getString("USALOCAL"))) {
						itemXmlCompra.setLocalPadrao(BigDecimal.ZERO);
					}

					if(rset.next()){
						itemXmlCompra.setCodProd(BigDecimal.ZERO);
					}
				}
			}

			boolean validaProdutoNaNota = true;
			if (BigDecimalUtil.getValueOrZero(itemXmlCompra.getCodProd()).intValue() == 0 || !temVolAlt(itemXmlCompra.getUnidade(), itemXmlCompra.getCodProd())) {
				if (prodsCadastrados != null && prodsCadastrados.containsKey(itemXmlCompra.getProdutoNfe())) {

					String prodCadastrado = prodsCadastrados.get(itemXmlCompra.getProdutoNfe());

					String codVol = prodCadastrado.substring(prodCadastrado.indexOf("-") + 1, prodCadastrado.length());
					itemXmlCompra.setUnidade(codVol);

					if (itemXmlCompra.getCodProd() == null) {
						String codProd = prodCadastrado.substring(0, prodCadastrado.indexOf("-"));
						itemXmlCompra.setCodProd(new BigDecimal(codProd));
					}
				} else {
					itensSemProduto.add(montaItensProduto(itemXmlCompra));
					validaProdutoNaNota = false;
				}
			}

			if (validaProdutoNaNota) {

				Collection<DynamicVO> itensVO = dwfEntityFacade.findByDynamicFinderAsVO(new FinderWrapper(DynamicEntityNames.ITEM_NOTA, "this.NUNOTA = ? AND this.CODPROD = ?", new Object[] { cabVo.asBigDecimal("NUNOTA"), itemXmlCompra.getCodProd() }));
				if (!itensVO.isEmpty()) {
					for (DynamicVO prodVO : itensVO) {
						int decQtd = prodVO.asDymamicVO("Produto").asInt("DECQTD");
						if (BigDecimalUtil.getRounded(prodVO.asBigDecimal("QTDUNIDPAD"), decQtd).intValue() != BigDecimalUtil.getRounded(itemXmlCompra.getQtdneg(), decQtd).intValue()) {
							throw new PersistenceException("Quantidade da nota diferente do XML para o produto: " + itemXmlCompra.getCodProd().intValue() + " Qtd. Xml: " + itemXmlCompra.getQtdneg() + " " + itemXmlCompra.getUnidade());
						}
					}
				} else {
					throw new PersistenceException("Produto " + itemXmlCompra.getCodProd().intValue() + " não localizado na nota.");
				}
				
			}

		}
		
		// valida se tem todos os items da nota no xml
		Collection<DynamicVO> todosItensVO = dwfEntityFacade.findByDynamicFinderAsVO(new FinderWrapper(DynamicEntityNames.ITEM_NOTA, "this.NUNOTA = ? ", new Object[] { cabVo.asBigDecimal("NUNOTA")}));
		for (DynamicVO prodVO : todosItensVO) {
			ItemXmlCompra itemXmlCompra = getItemXmlCompra(prodVO.asInt("CODPROD"), itensXmlCompra);
			if (itemXmlCompra == null) {
				throw new PersistenceException("Produto " + prodVO.asInt("CODPROD") + " não existe no XML.");
			}
		}

		dwfEntityFacade.removeByCriteria(new FinderWrapper(DynamicEntityNames.ITEM_NOTA, "this.NUNOTA = ?", new Object[] { cabVo.asBigDecimal("NUNOTA") }));

		if (BigDecimalUtil.getValueOrZero(nuNotaOrig).intValue() != 0) {
			NativeSql queValidaTGFVAR = new NativeSql(dwfEntityFacade.getJdbcWrapper());
			queValidaTGFVAR.appendSql(" SELECT 1 ");
			queValidaTGFVAR.appendSql(" FROM (SELECT SUM(VAR.QTDATENDIDA) AS ATENDIDA ");
			queValidaTGFVAR.appendSql(" , SUM(ITE.QTDNEG) AS QTDNEG ");
			queValidaTGFVAR.appendSql(" FROM TGFVAR VAR ");
			queValidaTGFVAR.appendSql(" , TGFITE ITE ");
			queValidaTGFVAR.appendSql(" WHERE ITE.NUNOTA = :NUNOTA ");
			queValidaTGFVAR.appendSql(" AND VAR.NUNOTA = ITE.NUNOTA ");
			queValidaTGFVAR.appendSql(" AND VAR.SEQUENCIA = ITE.SEQUENCIA)  VI ");
			queValidaTGFVAR.appendSql(" WHERE VI.ATENDIDA <> VI.QTDNEG ");

			queValidaTGFVAR.setNamedParameter("NUNOTA", cabVo.asBigDecimal("NUNOTA"));

			ResultSet rset = queValidaTGFVAR.executeQuery();

			if (rset.next()) {
				throw new PersistenceException("Existe itens da nota sem pedido de origem.");
			}
		}

		return itensSemProduto;
	}

	private ItemXmlCompra getItemXmlCompra(int codprod, Collection<ItemXmlCompra> itensXmlCompra) {
		for (ItemXmlCompra itemXmlCompra : itensXmlCompra) {
			if (itemXmlCompra.getCodProd().intValue() == codprod) {
				return itemXmlCompra;
			}
		}
		return null;
	}

	private boolean temVolAlt(String codvol, BigDecimal codprod) throws SQLException, Exception {
		NativeSql nativeSQL = new NativeSql(dwfEntityFacade.getJdbcWrapper());
		nativeSQL.appendSql("SELECT 1 FROM ");
		nativeSQL.appendSql("	TGFVOL VOL ");
		nativeSQL.appendSql("	WHERE ");
		nativeSQL.appendSql("		VOL.CODVOL = ? AND (");
		nativeSQL.appendSql("			EXISTS( ");
		nativeSQL.appendSql("				SELECT 1 FROM TGFPRO PRO ");
		nativeSQL.appendSql("				WHERE ");
		nativeSQL.appendSql("					PRO.CODPROD = ? AND ");
		nativeSQL.appendSql("					PRO.CODVOL = VOL.CODVOL ");
		nativeSQL.appendSql("			) OR ");
		nativeSQL.appendSql("			EXISTS( ");
		nativeSQL.appendSql("	   			SELECT 1 FROM TGFVOA VOA ");
		nativeSQL.appendSql("				WHERE ");
		nativeSQL.appendSql("	   				VOA.CODVOL = VOL.CODVOL AND ");
		nativeSQL.appendSql("	   				VOA.CODPROD = ? AND ");
		nativeSQL.appendSql("	   				VOA.ATIVO = 'S' ");
		nativeSQL.appendSql("			) ");
		nativeSQL.appendSql("		)");

		nativeSQL.addParameter(codvol);
		nativeSQL.addParameter(codprod);
		nativeSQL.addParameter(codprod);

		if (!nativeSQL.executeQuery().next()) {
			return false;
		}
		return true;
	}

	private void validaTransportadora(TransportadoraXmlCompra transportadoraXmlCompra) throws Exception {
		cabVo.setProperty("CIF_FOB", transportadoraXmlCompra.getCifFob());

		if (transportadoraXmlCompra.hasRetTranspInfo()) {
			cabVo.setProperty("VLRFRETE", BigDecimalUtil.getValueOrZero(transportadoraXmlCompra.getVlrFrete()));
			cabVo.setProperty("BASEICMSFRETE", BigDecimalUtil.getValueOrZero(transportadoraXmlCompra.getBaseIcmsFrete()));
			cabVo.setProperty("ICMSFRETE", BigDecimalUtil.getValueOrZero(transportadoraXmlCompra.getIcmsFrete()));
		}

		if (transportadoraXmlCompra.hasVolInfo()) {
			cabVo.setProperty("QTDVOL", transportadoraXmlCompra.getQtdvol());
			cabVo.setProperty("VOLUME", transportadoraXmlCompra.getVolume());
			cabVo.setProperty("MARCA", transportadoraXmlCompra.getMarca());
			cabVo.setProperty("NUMERACAOVOLUMES", transportadoraXmlCompra.getNumeracaoVolumes());
			cabVo.setProperty("PESO", transportadoraXmlCompra.getPeso());
			cabVo.setProperty("PESOBRUTO", transportadoraXmlCompra.getPesoBruto());
			cabVo.setProperty("LACRES", transportadoraXmlCompra.getLacres());
		}

		String cpfCnpj = null;
		//		String cpfCnpjName = null;

		if (transportadoraXmlCompra.getCnpj() != null) {
			cpfCnpj = transportadoraXmlCompra.getCnpj();
			//			cpfCnpjName = "CNPJ: ";
		} else {
			cpfCnpj = transportadoraXmlCompra.getCpf();
			//			cpfCnpjName = "CPF: ";
		}

		if (cpfCnpj != null) {

			/*addTranspMessage(cpfCnpjName + cpfCnpj);
			addTranspMessage("Nome: " + transportadoraXmlCompra.getNome());
			addTranspMessage(transportadoraXmlCompra.getCidade() + " - " + transportadoraXmlCompra.getUf());
			*/
			FinderWrapper finder = new FinderWrapper(DynamicEntityNames.PARCEIRO, "this.CGC_CPF = ?", new Object[] { cpfCnpj });
			Collection<DynamicVO> transportadorasVO = dwfEntityFacade.findByDynamicFinderAsVO(finder);

			if (transportadorasVO.isEmpty()) {
				//				addErrosCab("Transportadora não encontrada.\n");
			} else {
				if (transportadorasVO.size() == 1) {
					DynamicVO tranportadoraVO = transportadorasVO.iterator().next();

					/*if (!transportadoraXmlCompra.getNome().equalsIgnoreCase(tranportadoraVO.asString("RAZAOSOCIAL")) && !transportadoraXmlCompra.getNome().equalsIgnoreCase(tranportadoraVO.asString("NOMEPARC"))) {
						addErrosCab("Nome da Transportadora diferente.");
					}
					String nomeCid = StringUtils.getNullAsEmpty(tranportadoraVO.asDymamicVO("Cidade").asString("NOMECID"));
					if (!transportadoraXmlCompra.getCidade().equalsIgnoreCase(nomeCid)) {
						addErrosCab("Cidade da Transportadora diferente. " + nomeCid);
					}
					String estado = StringUtils.getNullAsEmpty(tranportadoraVO.asDymamicVO("Cidade").asDymamicVO("UnidadeFederativa").asString("UF"));
					if (!transportadoraXmlCompra.getUf().equalsIgnoreCase(estado)) {
						addErrosCab("Estado do Transportadora diferente. " + estado);
					}
					if (!tranportadoraVO.asBoolean("ATIVO")) {
						addErrosCab("Transportadora inativa.");
					}
					if (!tranportadoraVO.asBoolean("TRANSPORTADORA")) {
						addErrosCab("Parceiro não marcado como Transportadora.");
					}*/
					cabVo.setProperty("CODPARCTRANSP", tranportadoraVO.asBigDecimalOrZero("CODPARC"));
					//	addErrosCab("\n");
				} else {
					//addErrosCab("Várias Transportadoras com o mesmo CNPJ/CPF.\n");
				}
			}

		}
	}

	private void validaFornecedor(FornecedorXmlCompra fornecedorXmlCompra) throws Exception {
		/*addFornMessage("CNPJ: " + fornecedorXmlCompra.getCnpj() + " IE: " + fornecedorXmlCompra.getIe());
		addFornMessage("Nome: " + fornecedorXmlCompra.getNome());
		addFornMessage("Razão: " + fornecedorXmlCompra.getRazao());
		addFornMessage(fornecedorXmlCompra.getCidade() + " - " + fornecedorXmlCompra.getUf() + " - " + fornecedorXmlCompra.getCep());*/

		Collection<DynamicVO> fornecedoresVO = dwfEntityFacade.findByDynamicFinderAsVO(new FinderWrapper(DynamicEntityNames.PARCEIRO, "this.CGC_CPF =?", new Object[] { fornecedorXmlCompra.getCnpj() }));

		if (fornecedoresVO.isEmpty()) {
			//addErrosCab("Fornecedor não encontrado.\n");
		} else {
			if (fornecedoresVO.size() == 1) {
				DynamicVO fornecedorVO = fornecedoresVO.iterator().next();

				/*	if (!fornecedorXmlCompra.getIe().equals(fornecedorVO.asString("IDENTINSCESTAD"))) {
						addErrosCab("Inscrição do Fornecedor diferente. " + StringUtils.getNullAsEmpty(fornecedorVO.asString("IDENTINSCESTAD")));
					}
					if (!fornecedorXmlCompra.getRazao().equalsIgnoreCase(fornecedorVO.asString("RAZAOSOCIAL"))) {
						addErrosCab("Razão Social do Fornecedor diferente. " + StringUtils.getNullAsEmpty(fornecedorVO.asString("RAZAOSOCIAL")));
					}
					if (!fornecedorXmlCompra.getNome().equalsIgnoreCase(fornecedorVO.asString("NOMEPARC"))) {
						addErrosCab("Nome do Fornecedor diferente. " + StringUtils.getNullAsEmpty(fornecedorVO.asString("NOMEPARC")));
					}
					String nomeCid = StringUtils.getNullAsEmpty(fornecedorVO.asDymamicVO("Cidade").asString("NOMECID"));
					if (!fornecedorXmlCompra.getCidade().equalsIgnoreCase(nomeCid)) {
						addErrosCab("Cidade do Fornecedor diferente. " + nomeCid);
					}
					String estado = StringUtils.getNullAsEmpty(fornecedorVO.asDymamicVO("Cidade").asDymamicVO("UnidadeFederativa").asString("UF"));
					if (!fornecedorXmlCompra.getUf().equalsIgnoreCase(estado)) {
						addErrosCab("Estado do Fornecedor diferente. " + estado);
					}
					if (!fornecedorXmlCompra.getCep().equals(fornecedorVO.asString("CEP"))) {
						addErrosCab("CEP do Fornecedor diferente. " + StringUtils.getNullAsEmpty(fornecedorVO.asString("CEP")));
					}
					if (!fornecedorVO.asBoolean("ATIVO")) {
						addErrosCab("Fornecedor inativo.");
					}
					if (!fornecedorVO.asBoolean("FORNECEDOR")) {
						addErrosCab("Parceiro não marcado como Fornecedor.");
					}
					addErrosCab("\n");*/
				cabVo.setProperty("CODPARC", fornecedorVO.asBigDecimalOrZero("CODPARC"));
			} else {
				//addErrosCab("Vários Fornecedores com o mesmo CNPJ.\n");

				for (DynamicVO fornecedorVO : fornecedoresVO) {
					boolean razaoIgual = fornecedorXmlCompra.getRazao().equalsIgnoreCase(fornecedorVO.asString("RAZAOSOCIAL"));
					boolean nomeIgual = fornecedorXmlCompra.getNome().equalsIgnoreCase(fornecedorVO.asString("NOMEPARC"));
					String nomeCid = StringUtils.getNullAsEmpty(fornecedorVO.asDymamicVO("Cidade").asString("NOMECID"));
					boolean cidadeIgual = fornecedorXmlCompra.getCidade().equalsIgnoreCase(nomeCid);
					String estado = StringUtils.getNullAsEmpty(fornecedorVO.asDymamicVO("Cidade").asDymamicVO("UnidadeFederativa").asString("UF"));
					boolean estadoIgual = fornecedorXmlCompra.getUf().equalsIgnoreCase(estado);

					if (razaoIgual && nomeIgual && cidadeIgual && estadoIgual) {
						cabVo.setProperty("CODPARC", fornecedorVO.asBigDecimalOrZero("CODPARC"));

						/*	if (!fornecedorVO.asBoolean("ATIVO")) {
								addErrosCab("Fornecedor inativo.");
							}
							if (!fornecedorVO.asBoolean("FORNECEDOR")) {
								addErrosCab("Parceiro não marcado como Fornecedor.");
							}
							addErrosCab("\n");*/
						break;
					}
				}
			}
		}
	}

	private void validaEmpresa(EmpresaXmlCompra empresaXmlCompra) throws Exception {

		FinderWrapper finder = new FinderWrapper(DynamicEntityNames.EMPRESA, "this.CGC = ?", new Object[] { empresaXmlCompra.getCnpj() });
		Collection<DynamicVO> empresasVO = dwfEntityFacade.findByDynamicFinderAsVO(finder);

		/*addEmpMessage("CNPJ: " + empresaXmlCompra.getCnpj() + " IE: " + empresaXmlCompra.getIe());
		addEmpMessage("Nome: " + empresaXmlCompra.getNome());
		addEmpMessage(empresaXmlCompra.getCidade() + " - " + empresaXmlCompra.getUf());*/

		if (empresasVO.isEmpty()) {
			//			addErrosCab("Empresa não encontrada.\n");
		} else {
			if (empresasVO.size() == 1) {

				DynamicVO empresaVO = empresasVO.iterator().next();

				/*if (!empresaXmlCompra.getIe().equals(empresaVO.asString("INSCESTAD"))) {
					addErrosCab("Inscrição da Empresa diferente. " + StringUtils.getNullAsEmpty(empresaVO.asString("INSCESTAD")));
				}
				if (!empresaXmlCompra.getNome().equalsIgnoreCase(empresaVO.asString("RAZAOSOCIAL")) && !empresaXmlCompra.getNome().equalsIgnoreCase(empresaVO.asString("NOMEFANTASIA"))) {
					addErrosCab("Nome da Empresa diferente. ");
				}
				String nomeCid = StringUtils.getNullAsEmpty(empresaVO.asDymamicVO("Cidade").asString("NOMECID"));
				if (!empresaXmlCompra.getCidade().equalsIgnoreCase(nomeCid)) {
					addErrosCab("Cidade da Empresa diferente. " + nomeCid);
				}
				String estado = StringUtils.getNullAsEmpty(empresaVO.asDymamicVO("Cidade").asDymamicVO("UnidadeFederativa").asString("UF"));
				if (!empresaXmlCompra.getUf().equalsIgnoreCase(estado)) {
					addErrosCab("Estado do Empresa diferente. " + estado);
				}
				if (!empresaVO.asDymamicVO("EmpresaFinanceiro").asBoolean("ATIVO")) {
					addErrosCab("Empresa inativa.");
				}
				addErrosCab("\n");*/
				cabVo.setProperty("CODEMP", empresaVO.asBigDecimalOrZero("CODEMP"));
			} else {
				//				addErrosCab("Várias Empresas com o mesmo CNPJ.\n");
			}
		}
	}

	private void validaChaveNfe(ParsedXmlNotaCompra xmlNotaCompra) throws Exception {
		if (!xmlNotaCompra.getInfNfeId().equals(cabVo.asString("CHAVENFE")) && StringUtils.getEmptyAsNull(cabVo.asString("CHAVENFE")) != null) {
			throw new Exception("Esta nota já possui outra chave.");
		}
		if (StringUtils.getEmptyAsNull(xmlNotaCompra.getInfNfeId()) != null) {
			BigDecimal codEmp = cabVo.asBigDecimal("CODEMP").intValue() == 0 ? BigDecimal.valueOf(-1) : cabVo.asBigDecimal("CODEMP");
			FinderWrapper finder = new FinderWrapper(DynamicEntityNames.CABECALHO_NOTA, "this.CHAVENFE = ? AND ( ? = -1 OR this.CODEMP = ? )", new Object[] { xmlNotaCompra.getInfNfeId(), codEmp, codEmp });
			Collection<DynamicVO> cabsNotaVo = dwfEntityFacade.findByDynamicFinderAsVO(finder);

			if (!cabsNotaVo.isEmpty()) {
				DynamicVO cabNotaVO = cabsNotaVo.iterator().next();

				if (cabVo.asBigDecimal("NUNOTA").intValue() != cabNotaVO.asBigDecimal("NUNOTA").intValue()) {
					throw new Exception("Esta chave já foi usada em outra nota. Nro Único: " + cabNotaVO.asBigDecimal("NUNOTA").intValue());
				}
			}
		}

		cabVo.setProperty("CHAVENFE", xmlNotaCompra.getInfNfeId());
	}

	private void gravar(ParsedXmlNotaCompra parsedXmlNotaCompra) throws Exception {
		JapeSessionContext.putProperty(ListenerParameters.CENTRAIS, Boolean.TRUE);

		CACHelper cacHelper = new CACHelper();
		Collection<PrePersistEntityState> itens = montarItens(parsedXmlNotaCompra);
		cacHelper.incluirAlterarItem(cabVo.asBigDecimal("NUNOTA"), ServiceContext.getCurrent(), itens, false);

		atualizaVar();
		validaFinanceiro(parsedXmlNotaCompra);
	}

	private void atualizaVar() throws Exception {
		Collection<DynamicVO> itensVO = dwfEntityFacade.findByDynamicFinderAsVO(new FinderWrapper(DynamicEntityNames.ITEM_NOTA, "this.NUNOTA = ?", new Object[] { cabVo.asBigDecimal("NUNOTA") }));
		NativeSql queNotaOrigem = new NativeSql(dwfEntityFacade.getJdbcWrapper());
		queNotaOrigem.loadSql(this.getClass(), "queItePedidoImportXML.sql");
		queNotaOrigem.setReuseStatements(true);

		queNotaOrigem.setNamedParameter("CODPARC", cabVo.asBigDecimal("CODPARC"));
		queNotaOrigem.setNamedParameter("CODEMP", cabVo.asBigDecimal("CODEMP"));
		queNotaOrigem.setNamedParameter("NUNOTAORIG", nuNotaOrig);

		for (DynamicVO itemVO : itensVO) {
			int dobQtdPed = 0;

			queNotaOrigem.setNamedParameter("CODPROD", itemVO.asBigDecimal("CODPROD"));

			ResultSet rset = queNotaOrigem.executeQuery();

			while (rset.next() && itemVO.asInt("QTDNEG") > dobQtdPed) {

				BigDecimal qtdAtendida = null;
				if (rset.getBigDecimal("QTD").doubleValue() > itemVO.asDouble("QTDNEG") - dobQtdPed) {
					qtdAtendida = BigDecimal.valueOf(itemVO.asDouble("QTDNEG") - dobQtdPed);
				} else {
					qtdAtendida = rset.getBigDecimal("QTD");
				}

				DynamicVO variosPedidosVO = (DynamicVO) dwfEntityFacade.getDefaultValueObjectInstance("CompraVendavariosPedido");
				variosPedidosVO.setProperty("NUNOTA", cabVo.asBigDecimal("NUNOTA"));
				variosPedidosVO.setProperty("SEQUENCIA", itemVO.asBigDecimal("SEQUENCIA"));
				variosPedidosVO.setProperty("STATUSNOTA", itemVO.asString("STATUSNOTA"));
				variosPedidosVO.setProperty("QTDATENDIDA", qtdAtendida);
				variosPedidosVO.setProperty("NUNOTAORIG", rset.getBigDecimal("NUNOTA"));
				variosPedidosVO.setProperty("SEQUENCIAORIG", rset.getBigDecimal("SEQUENCIA"));

				dobQtdPed = dobQtdPed + qtdAtendida.intValue();

				dwfEntityFacade.createEntity("CompraVendavariosPedido", (EntityVO) variosPedidosVO);
			}
			rset.close();
		}
	}

	private void atualizaDataEstoque(ItemXmlCompra itemXmlCompra, LoteXmlCompra lote) throws Exception {
		EstoqueLoteInfo loteInfo = new EstoqueLoteInfo(cabVo.asBigDecimal("CODEMP"), itemXmlCompra.getCodProd(), itemXmlCompra.getLocalPadrao(), lote.getLote());

		ControleLoteHelper controleLote = new ControleLoteHelper(loteInfo);

		if (lote.getDtVal() != null) {
			controleLote.salvarValidadeEstoque(lote.getDtVal(), dwfEntityFacade);
		}

		if (lote.getDtFab() != null) {
			controleLote.salvarFabricacaoEstoque(lote.getDtFab(), dwfEntityFacade);
		}

		JapeSession.putProperty(ListenerParameters.IMPORTACAO_XML_NOTA_COMPRA_LOTE, Boolean.TRUE);
	}

	private Collection<PrePersistEntityState> montarItens(ParsedXmlNotaCompra parsedXmlNotaCompra) throws Exception {
		Collection<PrePersistEntityState> itens = new ArrayList<PrePersistEntityState>();
		CentralItemNota central = new CentralItemNota();

		for (ItemXmlCompra itemXmlCompra : parsedXmlNotaCompra.getItens()) {

			DynamicVO itemVO = (DynamicVO) dwfEntityFacade.getDefaultValueObjectInstance("ItemNota");

			itemVO.setProperty("NUNOTA", cabVo.asBigDecimal("NUNOTA"));
			itemVO.setProperty("CODPROD", itemXmlCompra.getCodProd());
			itemVO.setProperty("CODVOL", itemXmlCompra.getUnidade());
			itemVO.setProperty("VLRTOT", itemXmlCompra.getVlrTot());
			itemVO.setProperty("CODTRIB", itemXmlCompra.getCodTrib());
			itemVO.setProperty("CODLOCALORIG", itemXmlCompra.getLocalPadrao());
			itemVO.setProperty("OBSERVACAO", itemXmlCompra.getObservacao());
			itemVO.setProperty("CODEMP", cabVo.asBigDecimalOrZero("CODEMP"));
			itemVO.setProperty("VLRDESC", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrDesc()));
			itemVO.setProperty("BASEIPI", BigDecimalUtil.getValueOrZero(itemXmlCompra.getBaseIpi()));
			itemVO.setProperty("VLRIPI", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrIpi()));
			itemVO.setProperty("ALIQIPI", BigDecimalUtil.getValueOrZero(itemXmlCompra.getAliqIpi()));
			itemVO.setProperty("BASEICMS", BigDecimalUtil.getValueOrZero(itemXmlCompra.getBaseIcms()));
			itemVO.setProperty("VLRICMS", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrIcms()));
			itemVO.setProperty("ALIQICMS", BigDecimalUtil.getValueOrZero(itemXmlCompra.getAliqIcms()));
			itemVO.setProperty("BASESUBSTIT", BigDecimalUtil.getValueOrZero(itemXmlCompra.getBaseSubstiT()));
			itemVO.setProperty("VLRSUBST", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrSubst()));
			itemVO.setProperty("QTDENTREGUE", BigDecimal.ZERO);

			if(itemXmlCompra.getLotes() != null && !itemXmlCompra.getLotes().isEmpty()){
				LoteXmlCompra lote = itemXmlCompra.getLotes().iterator().next();
				itemVO.setProperty("CONTROLE", lote.getLote());
			}
			
			itemVO.setProperty("VLRUNIT", itemXmlCompra.getVlrUnit());
			central.recalcularValores("VLRUNIT", "0", itemVO, cabVo.asBigDecimal("NUNOTA"));

			itemVO.setProperty("QTDNEG", BigDecimalUtil.getValueOrZero(itemXmlCompra.getQtdneg()));
			central.recalcularValores("QTDNEG", "0", itemVO, cabVo.asBigDecimal("NUNOTA"));

			itemVO.setProperty("VLRDESC", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrDesc()));
			central.recalcularValores("VLRDESC", "0", itemVO, cabVo.asBigDecimal("NUNOTA"));
			
			itens.add(PrePersistEntityState.build(dwfEntityFacade, "ItemNota", itemVO, null, null));

			if (itemXmlCompra.getLotes() != null && !itemXmlCompra.getLotes().isEmpty()) {
				LoteXmlCompra lote = itemXmlCompra.getLotes().iterator().next();
				atualizaDataEstoque(itemXmlCompra, lote);

				valoresLote = new HashMap<String, BigDecimal>();
				valoresLote.put("VLRTOT", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrTot()));
				valoresLote.put("BASEIPI", BigDecimalUtil.getValueOrZero(itemXmlCompra.getBaseIpi()));
				valoresLote.put("VLRIPI", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrIpi()));
				valoresLote.put("BASEICMS", BigDecimalUtil.getValueOrZero(itemXmlCompra.getBaseIcms()));
				valoresLote.put("VLRICMS", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrIcms()));
				valoresLote.put("BASESUBSTIT", BigDecimalUtil.getValueOrZero(itemXmlCompra.getBaseSubstiT()));
				valoresLote.put("VLRSUBST", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrSubst()));
				valoresLote.put("VLRDESC", BigDecimalUtil.getValueOrZero(itemXmlCompra.getVlrDesc()));

				itens.addAll(explodirLotes(itemXmlCompra, itemVO));
			}
		}

		return itens;
	}

	private Collection<PrePersistEntityState> explodirLotes(ItemXmlCompra itemXmlCompra, DynamicVO itemVO) throws Exception {
		Collection<PrePersistEntityState> novosItens = new ArrayList<PrePersistEntityState>();

		boolean isfirst = true;
		if (itemXmlCompra.getLotes() == null) {
			return novosItens;
		}

		CentralItemNota central = new CentralItemNota();
		for (Iterator<LoteXmlCompra> iterator = itemXmlCompra.getLotes().iterator(); iterator.hasNext();) {
			LoteXmlCompra lotesXmlCompra = iterator.next();

			if (isfirst) {
				itemVO.setProperty("QTDNEG", lotesXmlCompra.getQtd());
				central.recalcularValores("QTDNEG", "0", itemVO, cabVo.asBigDecimal("NUNOTA"));
				ajustaValoresLotes(!iterator.hasNext(), itemVO, lotesXmlCompra);
				isfirst = false;
			} else {
				DynamicVO itemDuplicado = itemVO.buildClone();

				itemDuplicado.setProperty("CONTROLE", lotesXmlCompra.getLote());
				itemDuplicado.setProperty("QTDNEG", lotesXmlCompra.getQtd());
				central.recalcularValores("QTDNEG", "0", itemDuplicado, cabVo.asBigDecimal("NUNOTA"));

				novosItens.add(PrePersistEntityState.build(dwfEntityFacade, "ItemNota", itemDuplicado, null, null));
			}
		}

		return novosItens;
	}

	private void ajustaValoresLotes(boolean ehUltimoRegistro, DynamicVO itemVO, LoteXmlCompra loteXmlCompra) {
		if (ehUltimoRegistro) {
			for (Entry<String, BigDecimal> entry : valoresLote.entrySet()) {
				itemVO.setProperty(entry.getKey(), entry.getValue());
			}
		} else {
			for (Entry<String, BigDecimal> entry : valoresLote.entrySet()) {
				if ("VLRTOT".equals(entry.getKey()) || BigDecimalUtil.getValueOrZero(entry.getValue()).intValue() == 0) {
					continue;
				}
				itemVO.setProperty(entry.getKey(), BigDecimalUtil.getRounded(loteXmlCompra.getQtd().multiply(itemVO.asBigDecimalOrZero(entry.getKey()), MATH_CTX).divide(itemVO.asBigDecimalOrZero(("QTDNEG")), MATH_CTX), 2));
				entry.setValue(entry.getValue().subtract(itemVO.asBigDecimalOrZero(entry.getKey())));
			}
			itemVO.setProperty("VLRTOT", BigDecimalUtil.getRounded(itemVO.asBigDecimalOrZero("QTDNEG").multiply(itemVO.asBigDecimalOrZero("VLRUNIT"), MATH_CTX), 2));
			valoresLote.put("VLRTOT", valoresLote.get("VLRTOT").subtract(itemVO.asBigDecimalOrZero("VLRTOT")));
		}
	}

	private void validaFinanceiro(ParsedXmlNotaCompra xmlCompra) throws Exception {
		Collection<PersistentLocalEntity> financeirosVO = dwfEntityFacade.findByDynamicFinder(new FinderWrapper(DynamicEntityNames.FINANCEIRO, "this.NUNOTA = ?", new Object[] { cabVo.asBigDecimal("NUNOTA") }));

		if (xmlCompra.getFinanceiros().size() > financeirosVO.size()) {
			int parcelasAdicionadas = xmlCompra.getFinanceiros().size() - financeirosVO.size();
			for (int i = 0; i < parcelasAdicionadas; i++) {
				DynamicVO finanVO = null;

				if (financeirosVO.isEmpty()) {
					continue;
				}

				for (Iterator iterator = financeirosVO.iterator(); iterator.hasNext();) {
					PersistentLocalEntity persistentLocalEntity = (PersistentLocalEntity) iterator.next();
					finanVO = (DynamicVO) persistentLocalEntity.getValueObject();
				}

				if (finanVO != null) {
					DynamicVO finanVOCopy = finanVO.buildClone();
					finanVOCopy.setProperty("NUFIN", null);
					finanVOCopy.setProperty("DESDOBRAMENTO", BigDecimalUtil.valueOf(finanVO.asString("DESDOBRAMENTO")).add(BigDecimal.valueOf(1)).toString());
					dwfEntityFacade.createEntity(DynamicEntityNames.FINANCEIRO, (EntityVO) finanVOCopy);
				}
			}
		} else if (xmlCompra.getFinanceiros().size() < financeirosVO.size()) {
			DynamicVO finanVO = null;

			for (Iterator iterator = financeirosVO.iterator(); iterator.hasNext();) {
				PersistentLocalEntity persistentLocalEntity = (PersistentLocalEntity) iterator.next();
				finanVO = (DynamicVO) persistentLocalEntity.getValueObject();
			}

			dwfEntityFacade.removeEntity(DynamicEntityNames.FINANCEIRO, new Object[] { finanVO.asBigDecimal("NUFIN") });
		}

		financeirosVO = dwfEntityFacade.findByDynamicFinder(new FinderWrapper(DynamicEntityNames.FINANCEIRO, "this.NUNOTA = ?", new Object[] { cabVo.asBigDecimal("NUNOTA") }));

		if (xmlCompra.getFinanceiros().size() == financeirosVO.size()) {
			Iterator iteFinanXml = xmlCompra.getFinanceiros().iterator();
			Iterator iteFinanVO = financeirosVO.iterator();

			while (iteFinanXml.hasNext()) {
				FinanceiroXmlCompra finanXmlCompra = (FinanceiroXmlCompra) iteFinanXml.next();
				PersistentLocalEntity persistentFinan = (PersistentLocalEntity) iteFinanVO.next();
				DynamicVO finanVO = (DynamicVO) persistentFinan.getValueObject();

				finanVO.setProperty("DTVENC", finanXmlCompra.getDtVenc());

				if (finanXmlCompra.getValor().intValue() != finanVO.asBigDecimal("VLRDESDOB").intValue()) {
					finanVO.setProperty("VLRDESDOB", finanXmlCompra.getValor());
				}
				finanVO.setProperty("HISTORICO", finanXmlCompra.getDup());

				try {
					finanVO.setProperty("NUMNOTA", BigDecimalUtil.valueOf(finanXmlCompra.getDup()));
				} catch (Exception ignored) {
				}

				persistentFinan.setValueObject((EntityVO) finanVO);
			}
		}
	}

	private Element montaItensProduto(ItemXmlCompra itemXmlCompra) {
		Element itemElement = new Element("item");

		itemElement.setAttribute("CODPROD", StringUtils.getNullAsEmpty(itemXmlCompra.getCodProd()));
		itemElement.setAttribute("DESCRPROD", StringUtils.getNullAsEmpty(itemXmlCompra.getProduto()));
		itemElement.setAttribute("QTDNEG", StringUtils.getNullAsEmpty(itemXmlCompra.getQtdneg()));
		itemElement.setAttribute("CODVOL", StringUtils.getNullAsEmpty(itemXmlCompra.getCodVol()));
		itemElement.setAttribute("DESCRVOL", StringUtils.getNullAsEmpty(itemXmlCompra.getUnidade()));
		itemElement.setAttribute("CODPRODNFE", StringUtils.getNullAsEmpty(itemXmlCompra.getProdutoNfe()));
		itemElement.setAttribute("CODPARC", StringUtils.getNullAsEmpty(cabVo.asBigDecimal("CODPARC")));

		return itemElement;

	}
}
