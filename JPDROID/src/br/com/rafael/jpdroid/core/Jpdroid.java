package br.com.rafael.jpdroid.core;

import static br.com.rafael.jpdroid.core.JpdroidObjectMap.getContentvalues;
import static br.com.rafael.jpdroid.core.JpdroidObjectMap.getFieldByAnnotation;
import static br.com.rafael.jpdroid.core.JpdroidObjectMap.getFieldsByForeignKey;
import static br.com.rafael.jpdroid.core.JpdroidObjectMap.getFieldsByRelationClass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import br.com.rafael.jpdroid.annotations.Column;
import br.com.rafael.jpdroid.annotations.PrimaryKey;
import br.com.rafael.jpdroid.annotations.RelationClass;
import br.com.rafael.jpdroid.annotations.ViewColumn;
import br.com.rafael.jpdroid.enums.RelationType;
import br.com.rafael.jpdroid.enums.ScriptPath;
import br.com.rafael.jpdroid.exceptions.JpdroidException;

/**
 * Classe singleton, respons�vel pelas opera��es de banco de dados.
 * 
 * @author Rafael Centenaro
 */
public class Jpdroid {

	TreeMap<String, String> entidades = new TreeMap<String, String>();

	/**
	 * Indica se existe conex�o aberta.
	 * 
	 * @return
	 */
	public boolean isOpen() {
		if (database == null) {
			return false;
		}
		return database.isOpen();
	}

	private SQLiteDatabase database;

	private JpdroidDbHelper dbHelper;

	private Context context;

	/**
	 * Retorna o contexto.
	 * 
	 * @return
	 */
	public Context getContext() {
		return context;
	}
	
  public Date  getDate() {
		Date d = Calendar.getInstance().getTime();
		return d;
	}

	/**
	 * Atribui o context
	 * 
	 * @param context
	 */
	public void setContext(Context context) {
		this.context = context;
	}

	/**
	 * Retorna o nome do banco.
	 * 
	 * @return
	 */
	public String getDatabaseName() {
		return databaseName;
	}

	/**
	 * Atribui um nome para o banco de dados.
	 * Nome padr�o:JpdroidDB
	 * Obs:N�o � necess�rio informar as extens�o do banco.
	 * 
	 * @param databaseName
	 */
	public void setDatabaseName(String databaseName) {

		if (databaseName.indexOf(".db") < 0) {
			this.databaseName = databaseName + ".db";
		} else {
			this.databaseName = databaseName;
		}
	}

	private String databaseName = "JpdroidDB.db";

	private CursorFactory factory;

	private int databaseVersion = 1;

	/**
	 * Retorna a vers�o do banco.
	 * 
	 * @return
	 */
	public int getDatabaseVersion() {
		return databaseVersion;
	}

	/**
	 * Vers�o do banco, por default a vers�o � 1. Quando a vers�o do banco for diferente a base de dados ser� atualizada.
	 * 
	 * @param databaseVersion
	 */
	public void setDatabaseVersion(int databaseVersion) {
		this.databaseVersion = databaseVersion;
	}

	/**
	 * Retorna inst�ncia do SQLiteDatabase.
	 * 
	 * @return
	 */
	public SQLiteDatabase getDatabase() {
		return database;
	}

	private JpdroidTransaction transaction = null;

	private static Jpdroid jpdroid = null;

	/**
	 * Retorna inst�ncia da classe Jpdroid.
	 * 
	 * @return
	 */
	public static Jpdroid getInstance() {
		if (jpdroid == null) {
			jpdroid = new Jpdroid();
			return jpdroid;
		}
		return jpdroid;
	}

	private Jpdroid() {

	}

	/**
	 * Abre conex�o com o banco de dados.
	 * 
	 * @throws JpdroidException
	 * @throws SQLException
	 */
	public void open() {

		if (!isOpen()) {

			try {
				validar();

				if (dbHelper == null) {
					dbHelper = new JpdroidDbHelper(getContext(), getDatabaseName(), factory, databaseVersion);
				}

				database = dbHelper.getWritableDatabase();

				transaction = new JpdroidTransaction(database);

				if (!database.isReadOnly()) {
					database.execSQL("PRAGMA foreign_keys = ON;");
				}
			} catch (Exception e) {

				Log.w("Erro open()", e.getMessage());
			}

		}
	}

	private void validar() throws JpdroidException {
		if (getContext() == null) {
			throw new JpdroidException("O atributo context � nulo, configure o contexto atrav�s do m�todo setContext()");
		}
		if (dbHelper != null && !dbHelper.isValid()) {
			throw new JpdroidException(
			    "Nenhuma entidade foi configurada, adicione as entidades atrav�s do m�todo addEntity().");
		}

	}

	/**
	 * Fecha conex�o com o banco de dados.
	 */
	public void close() {
		if (isOpen()) {
			dbHelper.close();
		}
	}

	/**
	 * Deleta registros da tabela de acordo com os par�metros.
	 * 
	 * @param table
	 * @param whereClause
	 * @param whereArgs
	 * @return 1:Sucesso, -1:Erro, 0:Falha
	 */
	public int delete(String table, String whereClause, String[] whereArgs) {
		int retorno = 0;
		try {
			transaction.begin();
			retorno = database.delete(table, whereClause, whereArgs);
			transaction.commit();
		} catch (Exception e) {
			transaction.end();
			Log.e("Erro Deletar", e.getMessage());
		} finally {
			transaction.end();
		}
		return retorno;
	}

	/**
	 * Deleta todos os registros da entidade.
	 * 
	 * @param entity
	 * @return 1:Sucesso, -1:Erro, 0:Falha
	 */
	public int deleteAll(Class<?> entity) {

		int retorno = delete(entity.getSimpleName(), "", null);

		return retorno;

	}

	/**
	 * Deleta registro referente a inst�ncia do objeto.
	 * 
	 * @param entity
	 */
	public void delete(Object entity) {

		delete(entity.getClass(), entity);
	}

	/**
	 * Deleta registros da entidade.
	 * 
	 * @param entity
	 * @param object - Pode ser uma lista de objetos ou um cursor.
	 * @return 1:Sucesso, -1:Erro, 0:Falha
	 */
	public int delete(Class<?> entity, Object object) {

		int retorno = 0;
		try {
			if (object instanceof List) {
				for (Object item : ((List<?>) object)) {
					delete(item);
				}
				retorno = 1;
			} else {
				StringBuilder whereClause = new StringBuilder();
				List<String> whereArgs = new ArrayList<String>();

				String columnName = null;

				Field[] fields = entity.getDeclaredFields();
				for (Field field : fields) {

					if (field.getType().getSimpleName().equalsIgnoreCase("Bitmap")
					    || field.getType().getSimpleName().equalsIgnoreCase("Byte[]")) {
						continue;
					}
					Column annotationColumn = field.getAnnotation(Column.class);

					if (annotationColumn != null) {
						if ("".equals(annotationColumn.name())) {
							columnName = field.getName();
						} else {
							columnName = annotationColumn.name();
						}

						try {
							if (object instanceof Cursor) {
								Cursor cursor = (Cursor) object;
								if(cursor.getColumnIndex(columnName) >= 0){
									
									if (whereClause.length() > 0) {
										whereClause.append(" AND ");
									}
									whereClause.append(columnName + " = ?");
									
									whereArgs.add(cursor.getString(cursor.getColumnIndex(columnName)));
								}
							} else {
								
								if (whereClause.length() > 0) {
									whereClause.append(" AND ");
								}
								
								whereClause.append(columnName + " = ?");
								
								field.setAccessible(true);
								whereArgs.add(String.valueOf(field.get(object)));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

				}
				if (whereClause.length() > 0) {
					retorno = delete(entity.getSimpleName(), whereClause.toString(),
					    whereArgs.toArray(new String[whereArgs.size()]));
				}
			}
		} catch (Exception e) {
			retorno = -1;
			Log.e("Erro delete()", e.getMessage());
		}
		return retorno;
	}

	/**
	 * Insere objeto no banco.
	 * 
	 * @param entity - Inst�ncia da entidade.
	 * @return - retorna o id
	 */
	private long insert(Object entity) {

		ContentValues values = getContentvalues(entity);

		long insertId = database.insert(entity.getClass().getSimpleName(), null, values);

		return insertId;
	}

	/**
	 * Atualiza registro no banco referente ao objeto.
	 * 
	 * @param entity - Inst�ncia da entidade.
	 * @return 1:Sucesso, -1:Erro, 0:falhou
	 */
	private long update(Object entity) {

		long insertId = 0;
		try {

			ContentValues values = getContentvalues(entity);

			StringBuilder whereClause = new StringBuilder();
			List<String> whereArgs = new ArrayList<String>();

			String columnName = null;

			Field[] fields = entity.getClass().getDeclaredFields();
			for (Field field : fields) {

				PrimaryKey annotationId = field.getAnnotation(PrimaryKey.class);
				Column annotationColumn = field.getAnnotation(Column.class);

				if (annotationId != null && annotationColumn != null) {

					if ("".equals(annotationColumn.name())) {
						columnName = field.getName();
					} else {
						columnName = annotationColumn.name();
					}
					if (whereClause.length() > 0) {
						whereClause.append(" AND ");
					}
					whereClause.append(columnName + " = ?");

					field.setAccessible(true);
					if (field.get(entity) == null || String.valueOf(field.get(entity)).equals("0")) {
						throw new JpdroidException("A coluna " + field.getName() + " n�o possui valor!");
					}
					whereArgs.add(String.valueOf(field.get(entity)));

				}

			}

			insertId = database.update(entity.getClass().getSimpleName(), values, whereClause.toString(),
			    whereArgs.toArray(new String[whereArgs.size()]));

		} catch (Exception e) {

			Log.e("Erro Update()", e.getMessage());
		}

		return insertId;
	}

	/**
	 * Cria consulta sql de acordo com os par�metros.
	 * 
	 * @param entity - Entidade
	 * @param restrictions - Clasula where
	 * @return - Cursor <br>
	 *         Dica:http://developer.android.com/reference/android/database/sqlite/SQLiteQueryBuilder.html
	 */
	@SuppressLint("DefaultLocale")
	public Cursor createQuery(Class<?> entity, String restrictions) {
		if (!restrictions.toUpperCase().contains("WHERE") && restrictions.length() > 0) {
			restrictions = " WHERE " + restrictions;
		}
		Cursor cursor = database.rawQuery("select * from " + entity.getSimpleName() + restrictions, null);
		return cursor;
	}

	/**
	 * Retorna todos os registros da entidade.
	 * 
	 * @param entity - Entidade
	 * @return - Cursor <br>
	 *         Dica:http://developer.android.com/reference/android/database/sqlite/SQLiteQueryBuilder.html
	 */
	public Cursor createQuery(Class<?> entity) {
		Cursor cursor = createQuery(entity, "");
		return cursor;
	}

	/**
	 * Adiciona as entidades para valida��o.
	 * 
	 * @param entity
	 */
	public void addEntity(Class<?> entity) {
		try {
			if (dbHelper == null) {
				dbHelper = new JpdroidDbHelper(getContext(), getDatabaseName(), factory, databaseVersion);
			}
			dbHelper.addClass(entity);
			entidades.put(entity.getSimpleName(), entity.getName());
		} catch (Exception e) {
			Log.e("Erro addEntity()", e.getMessage());
		}

	}

	/**
	 * Retorna uma lista de objetos preenchidos.
	 * 
	 * @param entity
	 * @return List<Object>
	 */
	public <T> List<T> getObjects(Class<T> entity) {
		return getObjects(entity, "", false);
	}

	/**
	 * Retorna uma lista de objetos preenchidos.
	 * 
	 * @param entity
	 * @param fillRelationClass - Indica se deve preencher as classes relacionadas.
	 * @return List<Object>
	 */
	public <T> List<T> getObjects(Class<T> entity, boolean fillRelationClass) {
		return getObjects(entity, "", fillRelationClass);
	}

	/**
	 * Retorna uma lista de objetos preenchidos.
	 * 
	 * @param entity
	 * @param restrictions
	 * @return List<Object>
	 */
	public <T> List<T> getObjects(Class<T> entity, String restrictions) {
		return getObjects(entity, restrictions, false);
	}

	/**
	 * Retorna uma lista de objetos preenchidos.
	 * 
	 * @param entity
	 * @param restrictions - Cl�usula where.
	 * @param fillRelationClass - Indica se deve preencher as classes relacionadas.
	 * @return List<Object>
	 */
	public <T> List<T> getObjects(Class<T> entity, String restrictions, boolean fillRelationClass) {

		Object retorno = null;

		List<T> entityList = new ArrayList<T>();
		try {

			if (restrictions.length() > 0) {
				restrictions = " where " + restrictions;
			}
			String columnName;
			Cursor cursor = database.rawQuery("select * from " + entity.getSimpleName() + restrictions, null);
			cursor.moveToFirst();
			if (cursor.getCount() == 0) {
				entityList.add(entity.newInstance());
				return entityList;
			}
			do {
				retorno = entity.newInstance();

				Field fieldPk = getFieldByAnnotation(retorno, PrimaryKey.class);

				Field[] fields = entity.getDeclaredFields();
				for (Field field : fields) {

					if (fillRelationClass) {
						RelationClass relationClass = field.getAnnotation(RelationClass.class);
						if (relationClass != null) {
							Class<? extends Object> ob = field.getType();

							field.setAccessible(true);

							String sql = "";

							if (ob.isAssignableFrom(List.class)) {

								ParameterizedType fieldGenericType = (ParameterizedType) field.getGenericType();
								Class<?> fieldTypeParameterType = (Class<?>) fieldGenericType.getActualTypeArguments()[0];

								sql = relationClass.joinColumn() + " = "
								    + cursor.getLong(cursor.getColumnIndex(String.valueOf(fieldPk.getName())));
								List<?> objetos = getObjects(fieldTypeParameterType, sql, fillRelationClass);
								if (objetos.size() > 0) {
									field.set(retorno, objetos);
								}
							} else {
								if ((relationClass.relationType() == RelationType.OneToMany)
								    || (relationClass.relationType() == RelationType.OneToOne)) {
									sql = "_id = ";
									sql += cursor.getLong(cursor.getColumnIndex(String.valueOf(relationClass.joinColumn())));
								} else {
									sql = relationClass.joinColumn() + " = ";
									sql += cursor.getLong(cursor.getColumnIndex(String.valueOf(fieldPk.getName())));
								}
								
								List<?> objetos = getObjects(ob, sql, fillRelationClass);
								if (objetos.size() > 0) {
									field.set(retorno, objetos.get(0));
								}
							}

						}
					}
					Column annotationColumn = field.getAnnotation(Column.class);
					if (annotationColumn != null) {
						if ("".equals(annotationColumn.name())) {
							columnName = field.getName();
						} else {
							columnName = annotationColumn.name();
						}
						field.setAccessible(true);

						if ("String".equalsIgnoreCase(field.getType().getSimpleName())						    
								|| ("java.util.Date".equals(field.getType().getSimpleName()))
						    || ("java.sql.Date".equals(field.getType().getSimpleName()))
						    || ("Calendar".equals(field.getType().getSimpleName()))) {

							field.set(retorno, cursor.getString(cursor.getColumnIndex(columnName)));

						} else if ("Boolean".equalsIgnoreCase(field.getType().getSimpleName())) {

							field.set(retorno, Boolean.valueOf(cursor.getString(cursor.getColumnIndex(columnName))));

						} else if ("Double".equalsIgnoreCase(field.getType().getSimpleName())) {

							field.set(retorno, cursor.getDouble(cursor.getColumnIndex(columnName)));

						} else if ("Float".equalsIgnoreCase(field.getType().getSimpleName())) {

							field.set(retorno, cursor.getFloat(cursor.getColumnIndex(columnName)));

						} else if (("Integer".equals(field.getType().getSimpleName()))
						    || ("int".equals(field.getType().getSimpleName()))
						    || ("Long".equalsIgnoreCase(field.getType().getSimpleName()))
						    || ("Short".equalsIgnoreCase(field.getType().getSimpleName()))) {

							field.set(retorno, cursor.getInt(cursor.getColumnIndex(columnName)));

						} else if (("Byte[]".equalsIgnoreCase(field.getType().getSimpleName()))
						    || ("Bitmap".equalsIgnoreCase(field.getType().getSimpleName()))) {
							byte[] blob = cursor.getBlob(cursor.getColumnIndex(columnName));
							if (blob != null) {
								Bitmap bmp = BitmapFactory.decodeByteArray(blob, 0, blob.length);
								field.set(retorno, bmp);
							}
						}
					}
					ViewColumn viewColumn = field.getAnnotation(ViewColumn.class);
					if (viewColumn != null) {
						field.setAccessible(true);
						field
						    .set(
						        retorno,
						        this.rawQuery(
						            "SELECT " + viewColumn.atributo() + " FROM " + viewColumn.entity() + " WHERE _id = "
						                + cursor.getLong(cursor.getColumnIndex(viewColumn.foreignKey())), null).getString(0));
					}

				}
				entityList.add(entity.cast(retorno));
			} while (cursor.moveToNext());
		} catch (Exception e) {
			Log.e("Erro getObjects()", e.getMessage());
		}
		return entityList;
	}

	/**
	 * Persiste um objeto ou uma lista no banco de dados.
	 * 
	 * @param entity
	 * @throws JpdroidException
	 */
	public void persist(Object entity) throws JpdroidException {

		try {
			transaction.begin();
			persistRecursivo(entity);
			transaction.commit();
		} catch (Exception e) {
			transaction.end();
			throw new JpdroidException(e.getMessage());
		} finally {
			transaction.end();
		}

	}

	/**
	 * M�todo recursivo para persist�ncia de objetos.
	 * 
	 * @param entity
	 * @return
	 */
	private long persistRecursivo(Object entity) {

		long idMaster = 0;
		try {
			// Persiste objetos da lista
			if (entity instanceof List) {
				for (Object item : ((List<?>) entity)) {
					persistRecursivo(item);
				}
			} else {
				// Classe relacionada de um para muitos
				Field[] fieldRelationClassOneToMany = getFieldsByRelationClass(entity, RelationType.OneToMany);
				if (fieldRelationClassOneToMany != null) {
					for (int i = 0; i < fieldRelationClassOneToMany.length; i++) {

						fieldRelationClassOneToMany[i].setAccessible(true);

						Object child = fieldRelationClassOneToMany[i].get(entity);
						if (child != null) {
							if (child instanceof List) {
								for (Object item : ((List<?>) child)) {
									long idItem = persistRecursivo(item);
									Field[] fieldForeingKeyList = getFieldsByForeignKey(entity, item.getClass().getSimpleName());

									if (fieldForeingKeyList != null) {

										for (int u = 0; u < fieldForeingKeyList.length; u++) {

											fieldForeingKeyList[u].setAccessible(true);
											fieldForeingKeyList[u].setLong(entity, idItem);
										}

									}
								}
							} else {
								long idItem = persistRecursivo(child);
								Field[] fieldForeingKeyList = getFieldsByForeignKey(entity, child.getClass().getSimpleName());

								if (fieldForeingKeyList != null) {

									for (int u = 0; u < fieldForeingKeyList.length; u++) {

										fieldForeingKeyList[u].setAccessible(true);
										fieldForeingKeyList[u].setLong(entity, idItem);
									}

								}
							}
						}
					}
				}

				Field fieldPk = getFieldByAnnotation(entity, PrimaryKey.class);
				if (fieldPk != null) {
					fieldPk.setAccessible(true);

					if (fieldPk.get(entity) == null || String.valueOf(fieldPk.get(entity)).equals("0")) {
						idMaster = insert(entity);
					} else {
						idMaster = Long.parseLong(String.valueOf(fieldPk.get(entity)));
						update(entity);
					}
				}
				// Pode existir mais de uma classe relacionada
				Field[] fieldRelationClassManyToOne = getFieldsByRelationClass(entity, RelationType.ManyToOne);

				if (fieldRelationClassManyToOne != null) {
					for (int i = 0; i < fieldRelationClassManyToOne.length; i++) {

						fieldRelationClassManyToOne[i].setAccessible(true);

						Object child = fieldRelationClassManyToOne[i].get(entity);
						if (child != null) {
							if (child instanceof List) {
								for (Object item : ((List<?>) child)) {
									// Pode existir mais de uma coluna foreinkey
									Field[] fieldForeingKeyList = getFieldsByForeignKey(item, entity.getClass().getSimpleName());

									if (fieldForeingKeyList != null) {

										for (int u = 0; u < fieldForeingKeyList.length; u++) {

											fieldForeingKeyList[u].setAccessible(true);
											fieldForeingKeyList[u].setLong(item, idMaster);
										}

									}
									persistRecursivo(item);
								}

							} else {
								Field[] fieldForeingKey = getFieldsByForeignKey(child, entity.getClass().getSimpleName());

								if (fieldForeingKey != null) {

									for (int u = 0; u < fieldForeingKey.length; u++) {

										fieldForeingKey[u].setAccessible(true);
										fieldForeingKey[u].setLong(child, idMaster);
									}
								}
								persistRecursivo(child);
							}
						}
					}
				}
			}

		} catch (Exception e) {
			idMaster = -1;
			Log.e("Erro persistRecursivo()", e.getMessage());
		}
		return idMaster;

	}

	/**
	 * @param table
	 * @param columns
	 * @param selection
	 * @param selectionArgs
	 * @param groupBy
	 * @param having
	 * @param orderBy
	 * @return cursor
	 */
	public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy,
	    String having, String orderBy) {
		return database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
	}

	/**
	 * @param sql
	 * @param selectionArgs
	 * @return cursor
	 */
	public Cursor rawQuery(String sql, String[] selectionArgs) {
		Cursor retorno = database.rawQuery(sql, selectionArgs);
		retorno.moveToFirst();
		return retorno;
	}

	/**
	 * Executa script sql.
	 * 
	 * @param sql
	 */
	public void execSQL(String sql) {
		database.execSQL(sql);
	}

	/**
	 * Importa e executa script sql. <br>
	 * <br>
	 * Ex: <br>
	 * UPSERT Combustivel (nome,preco) VALUES("Gasolina",3.11); <br>
	 * INSERT OR REPLACE INTO combustivel (nome) values("Gasolina");
	 * 
	 * @param scriptUri <BR>
	 *          ScriptUri.Assets - Diret�rio Assets do projeto. <BR>
	 *          ScriptUri.SdCard - Diret�rio do cart�o SD.
	 * @param fileName <BR>
	 *          Nome Arquivo.
	 */
	public int importSqlScript(ScriptPath scriptUri, String fileName) {
		try {
			String readLine = "";
			BufferedReader reader = null;
			if (scriptUri == ScriptPath.Assets) {
				reader = new BufferedReader(new InputStreamReader(getContext().getAssets().open(fileName), "ISO-8859-15"));
				
			} else {
				if (Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
					File dir = Environment.getExternalStorageDirectory();
					File file = new File(dir, fileName);
					if(!file.exists()){
						return 0;
					}
					reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-15"));
				} else {
					throw new JpdroidException("Nenhum cart�o de mem�ria foi localizado!");
				}
			}
			StringBuffer script = new StringBuffer();
			while ((readLine = reader.readLine()) != null) {
				script.append(readLine);
			}

			String[] lines = script.toString().split(";");

			transaction.begin();

			for (String line : lines) {
				upsert(line);
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.end();
			Log.e("Erro Importar arquivo sql.", e.getMessage());
			return -1;
		} finally {
			transaction.end();
		}
		return 1;

	}

	/**
	 * UPSERT / UPDATE OR INSERT <br>
	 * M�todo respons�vel por inserir novos registros ou atualizar registros existentes. <br>
	 * <br>
	 * O comando deve respeitar a seguinte sintaxe, lembrando que a sintaxe � case sensitive. <br>
	 * Ex:UPSERT NomeEntidade (Coluna1,Coluna2) VALUES(Valor1,Valor2); <br>
	 * <br>
	 * Requisito: possuir ao menos uma coluna do tipo unique.
	 * <br>Os espa�os das strings devem ser preenchidos com o caractere '#'.
	 * <br>Exemplo:
	 * <br>UPSERT Cidade (_id,nome,id_Estado) VALUES(0,"Dion�sio Cerqueira",6);
	 * <br>UPSERT Cidade (_id,nome,id_Estado) VALUES(0,"Dion�sio#Cerqueira",6);
	 * 
	 * @param sql
	 * @throws Exception
	 */
	public void upsert(String sql) throws Exception {

		if (sql.contains("UPSERT")) {

			String comando[] = null;
			String colunas[] = null;
			String valores[] = null;
			String replace = null;
			String where = " WHERE 0 = 0 ";
			TreeMap<String, String> chaveValor = new TreeMap<String, String>();

			String query = "", sqlExec = "";

			comando = sql.split(" ");
			replace = comando[2].replace('(', ' ').replace(')', ' ');
			colunas = replace.split(",");

			replace = comando[3].substring(7).replace(')', ' ');
			valores = replace.split(",");

			if (colunas.length != valores.length) {
				throw new JpdroidException("O n�mero de colunas n�o corresponde ao n�mero de valores!");
			}
			for (int i = 0; i < colunas.length; i++) {
				chaveValor.put(colunas[i].trim(), valores[i].trim());
			}

			Class<?> classe = Class.forName(entidades.get(comando[1]));
			Field[] fields = classe.getDeclaredFields();
			query = "SELECT * FROM " + comando[1];

			for (Field field : fields) {
				Column column = field.getAnnotation(Column.class);

				if (column != null && column.unique()) {
					String columName = column.name();
					if (chaveValor.containsKey(columName)) {
						where += " and " + columName + " = " + chaveValor.get(columName);
					} else {
						throw new JpdroidException("Coluna do tipo unique n�o encontrada na tabela " + comando[1] + ".");
					}
				}
			}
			if(where.equals(" WHERE 0 = 0 ")){
				for (String col : colunas) {
						where += " and " + col.trim() + " = " + chaveValor.get(col.trim());
				}
			}
			if(where.equals(" WHERE 0 = 0 ")){
				throw new JpdroidException("Coluna do tipo unique n�o encontrada no script. Colunas marcadas como unique s�o obrigatorias nos comandos UPSERT.");
			}

			where = where.replaceAll("#", " ");
			if (database.rawQuery(query + where, null).getCount() == 0) {
				sqlExec = sql.replaceAll("UPSERT", "INSERT INTO");
				sqlExec = sqlExec.replaceAll("#", " ");
				database.execSQL(sqlExec);
			} else {
				sqlExec = "UPDATE " + comando[1];
				for (int i = 0; i < colunas.length; i++) {
					if (i > 0) {
						sqlExec += ",";
					} else {
						sqlExec += " SET ";
					}
					sqlExec += colunas[i] + " = " + chaveValor.get(colunas[i].trim());
				}
				where = where.replaceAll("#", " ");
				sqlExec += where;
				sqlExec = sqlExec.replaceAll("#", " ");
				database.execSQL(sqlExec);
			}

		} else {
			database.execSQL(sql);
		}

	}

	/**
	 * Compacta o banco Sqlite, o tamanho do arquivo do sqlite diminuir�.
	 */
	public void vacuum() {
		database.execSQL("VACUUM");

	}

	/**
	 * Indica se � a primeira execu��o do programa.
	 * 
	 * @return boolean
	 */
	public boolean isCreate() {
		return dbHelper.isCreate();
	}

}