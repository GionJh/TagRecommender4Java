package com.tag4j.recommendation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

import weka.associations.AssociationRule;
import weka.associations.AssociationRules;
import weka.associations.FPGrowth;
import weka.associations.Item;
import weka.core.Instances;

/**
 * <pre>
 *  A Recommender object uses an association rules based approach to
 *  carry out the job of recommending tags ideally related to the ones an item is already marked with or
 *  to its title if the former are missing.
 *  These association rules are extracted from a list of tagsets/transactions
 *  provided by the user.
 *  
 *  A recommender object is bound to a workspace folder,
 *  wich is created inside the current directory, the name of the folder must be provided in the
 *  constructor.
 * 
 *  @author RaffaeleEsposito
 * </pre>
 */
public class Recommender
{
	private static double  DELTA = 0.009;
	private static String sep = java.io.File.separator;
	
	private int MAX_NUM_ITEMS_IN_TRANS;
	
	private File myFile;
	
	private String NOME_TAGS_FILE = "tags_file_ser";
	private String NOME_RULES_FILE = "rules_file_ser";
	
	private MiscellaneousUtils mu = new MiscellaneousUtils();
	
	//temp
	//private static AssociationRules arules;

	/**
	 * Creates a Recommender object.
	 * 
	 * @param idDir id of the workspace, must not be negative
	 */
	public Recommender(int idDir)
	{	
		if(idDir <= 0 )
			throw new IllegalArgumentException();  
		 
		 File curDir = new File(System.getProperty("user.dir"));
		 
		 File nf = new File("" + idDir);
			
	  	 if(nf.isDirectory() && nf.getAbsolutePath().startsWith(curDir.getAbsolutePath()))
		 {
			 System.out.println("esiste");
			 System.out.println(nf.getAbsolutePath());
			 myFile = nf;
			 return;
		 }
	  	 else
	  	 {
	  		 System.out.println("creato con nome dato");
	  		 File nff = new File(""+idDir); 		 
			 nff.mkdir();
			 myFile = nff;
			 return;
	  	 }	 
	}
	
	/**
	 * <pre>
	 * 
	 * This method lets the Recommender build the files it needs to perform recommendation
	 * activity, (i.e. for the method suggestTags to work).
	 * The files are builded in the current directory, nor their content or nature are
	 * important for the user of this API.
	 * 
	 * The threshold used is a function of the number of tags present in the list of transactions
	 * provided.
	 * It is the result of an heuristic approach to the problem.
	 * More details about this in the documentation associated to this API.
	 *
	 * @param confidence if negative, default confidence (0.1) will be used,
	 *        confidence cannot be > 1
	 *        
	 * @param transactions cannot be null or of size = 0
	 *        transactions cannot containt empty or null elements.
	 * 
	 * @throws Exception on File I/O or file format problems
	 * 
	 * </pre>
	 */
	public void computeRules(double confidence,List<List<String>> transactions) throws Exception
	{	
		if(transactions == null || transactions.size() == 0)
			throw new IllegalArgumentException();
		
		if(!isOkTransactions(transactions))
			throw new IllegalArgumentException();
		
		if(confidence > 1)
			throw new IllegalArgumentException();
		
		if(confidence < 0)
			confidence = 0.1;

		// generate .arff from file
		ArffGenerator arffGen = new ArffGenerator(transactions,myFile.getAbsolutePath());

		System.out.println("YYYYYYYYY"+arffGen.getTagCounter());
		
		FPGrowth fpGrowth = new FPGrowth();
		//fpGrowth.setNumRulesToFind(1000);
	    fpGrowth.setFindAllRulesForSupportLevel(true);

        fpGrowth.setMinMetric(confidence); // <--- confidenza

        int maxEl = findMaxElementInTransaction(transactions);
        MAX_NUM_ITEMS_IN_TRANS = maxEl;
        
        // supporto minimo da cambiare in base alle euristiche
        double prc = getThresholdEuristic(arffGen.getTagCounter(),arffGen.getTransactionCounter(),maxEl);
        System.out.println("######-----------"+ prc);
        
        fpGrowth.setLowerBoundMinSupport(prc); 
        fpGrowth.setDelta(DELTA);

        System.out.println(arffGen.getOutFile().getName());
        BufferedReader datafile = mu.readDataFile(arffGen.getOutFile().getAbsolutePath());
        Instances data = new Instances(datafile);
        
        fpGrowth.buildAssociations(data);
        
        AssociationRules arules = fpGrowth.getAssociationRules();
        
        mu.serializeRulesOnFile(myFile.getAbsolutePath()+sep+NOME_RULES_FILE, arules);
        mu.serializeTagsOnFile(myFile.getAbsolutePath()+sep+NOME_TAGS_FILE,arffGen.getAllTheTags());

		for(AssociationRule  ar : arules.getRules() )
 			  System.out.println(ar);

        System.out.println("EXIT");
	}

	/**<pre>
	 * 
	 * @return the generation date of the files the recommender needs to recommend tags,
	 * 		   (i.e these file are generated by calling computeRules)
	 *         null if these files are not existent yet
	 * @throws IOException
	 * 
	 * </pre> 
	 */
	public FileTime getFilesGenerationDate() throws IOException
	{
		File tags = new File(myFile.getAbsolutePath()+sep+NOME_TAGS_FILE);
		File rules = new File(myFile.getAbsolutePath()+sep+NOME_RULES_FILE);
		
		if(tags.exists() == false || rules.exists() ==  false)
			return null;
		
		BasicFileAttributes attr = Files.readAttributes(tags.toPath(), BasicFileAttributes.class);
		
		return attr.lastModifiedTime();
	}
	
	/**
	 * <pre>
	 * Tags already assigned to an item are used to recommend new tags.
	 * If there are not tags assigned, we try to use the title instead.
	 * 
	 * @param tags cannot be null, it can be size == 0
	 * 	      in this case title will be used to come up
	 * 		  with a list of suggested tags.
	 * 
	 * @param title if tags.size == 0, title cannot be null or empty.
	 * 
	 * @return recommended list of tags
	 * @throws IOException if the files needed are not generated, some format problem occur,
	 * 		   or if some I/O problem occurs.
	 * 		   The method computeRules is used to build the files needed.
	 * 		   The files are created in the current dirctory.
	 * 
	 * </pre>
	 */
	public List<String> suggestTagsOnTaggedMashup(List<String> tags,String title)
			throws IOException
	{	
		// controllare che tags non sia null
		if(tags == null)
			throw new IllegalArgumentException("tags cannot be null");
		
		if(tags.size() == 0)
		{
			if(title == null || title.equalsIgnoreCase(""))
				throw new IllegalArgumentException("title cannot be null or empty");
			
				// deserializza tags
				List<String> alltags;

				 try
			   	 {
				  alltags = mu.deserializeTagOnFile(myFile.getAbsolutePath()+sep+NOME_TAGS_FILE);
			   	  
			   	  if(alltags == null || alltags.size() == 0)
			   		  throw new Exception();

			   	 }
			   	 catch(Exception ex)
			   	 {
			   		 throw new IOException("tag file I/O error");
			   	 }

		       // popola con tag estratti dal titolo
    			tags = extractTagsFromTitle(title,alltags);	
    			return tags;
    	}	
    	 
		  AssociationRules arules;
		  
	    	 try
	    	 {
	    	  arules = mu.deserializeRulesOnFile(myFile.getAbsolutePath()+sep+NOME_RULES_FILE);
	    	  
	    	  if(arules == null)
	    		  throw new Exception();
	    	  
	    	 }
	    	 catch(Exception ex)
	    	 {
	    		 throw new IOException("rules file I/O error");
	    	 }

		 List<Collection<Item>> conseqToConsider = new  ArrayList<Collection<Item>>();
		 
		 int counter = 0;
		 do
		 {	 
    		 for(int j = 0; j < arules.getRules().size();j++)
    		 {	
	    			 if(getMatchesInPremise(tags, arules.getRules().get(j)) >= ((MAX_NUM_ITEMS_IN_TRANS-1) - counter) ) 
	    			    conseqToConsider.add(arules.getRules().get(j).getConsequence());
		     }	 
		 
    		 counter++;
		 }
		 while(conseqToConsider.size() <= 4 && counter < (MAX_NUM_ITEMS_IN_TRANS -1)  ); // CONSIDERARE COSTANTE FINAL
	
		 // costruire la lista di suggerimenti
		 List<String> recommendedTags = new ArrayList<String>();

		 for(Collection<Item> coll : conseqToConsider)
		 {
			 Iterator<Item> iter = coll.iterator();
			 
			 while(iter.hasNext())
			 {
				 Item it = iter.next();
				 
				 // se non è già stato suggeirito
				 // e non è presente tra i tag già assegnati
				 if(notPresent(it.getAttribute().name(),recommendedTags)
						 && notPresent(it.getAttribute().name(),tags))
				 {
					 // add suggerimento
					 recommendedTags.add(it.getAttribute().name());
				 }	 
			 }	 
		 }
		 
		 if(recommendedTags.size() == 0)
			 return null;
		 
		// Limita a LIMIT
		return  recommendedTags.subList(0, Math.min(recommendedTags.size(), 5)); // return empty if recommendedTags.size() == 0
	}
	
	private boolean isOkTransactions(List<List<String>> transactions)
	{
		for(List<String> in : transactions)
		{
			if(in == null || in.size() == 0)
				return false;
		}
		
		return true;
	}
	
	private int findMaxElementInTransaction(List<List<String>> lista)
	{
		int max = -1;
		
		for(List<String> in : lista)
		{
			if(in.size() > max)
				max = in.size();
		}
		
		return max;
	}

	 
	/*
	 * 
	 */
	 private double getThresholdEuristic(int nOfTags,int nOfTrans,int maxItemInTransaction)
	 {
		 // formula:
		 // (1/(|X|*N)) * (  1 + 2 + 3 + 4 + 5 + ... + N  )
		 
		 int sum = 0;
		 int max = maxItemInTransaction;
		 for(;max > 0; max--)
			 sum = sum + max;

		 double mySum = (double) sum/ (double) maxItemInTransaction;
		 
		 double nCoeff = (double) mySum / (double) (nOfTags);
		 
		 double n =  nCoeff * nOfTrans;
		 
		 if(n >= 3)
			 return nCoeff;
		 else
			 return (double) 3/nOfTrans;
	 }
	 
	/* 
	 * quanti elementi della premessa compaiono in tags
	 * intersezione tra premessa e tag
	 */
	private int getMatchesInPremise(List<String> tags,AssociationRule ar)
	{	
		Iterator<Item> iter = ar.getPremise().iterator();

		int counter = 0;
		
		while(iter.hasNext())
		{
			String itemString = iter.next().getAttribute().name();
			
			for(String t : tags)
			{
				if(t.equalsIgnoreCase(itemString))
					counter++;
			}	
		}
					
		return counter;		
	}

	private boolean notPresent(String name,List<String> al)
	{
		for(String s : al)
		{
			if(s.equalsIgnoreCase(name))
				return false;
		}
		
		return true;
	}

	private List<String> extractTagsFromTitle(String title,List<String> tags) throws IOException
	{
		if(title.endsWith("?"))
			title = title.substring(0, title.length()-1);
		
		List<String> words = mu.removeStopWords(title);
		
		// cerca tag
		List<String> myTags = findAffineTags(words,tags);
	    
		return myTags;
	}
	
	private List<String> findAffineTags(List<String> words,List<String> tags)
	{	
		HashMap<String,Double> map = new HashMap<String,Double>();
		
			for(String word : words)
			{
				double min = Integer.MAX_VALUE;
				String minAss = "";
				
				for(String in : tags)
				{
					if(StringUtils.getLevenshteinDistance(word, in) < min)
					{
						min = StringUtils.getLevenshteinDistance(word, in);
						minAss = in;
					}	
				}
				
				if(min <= 4)  // take into considerations min 4 levenstein distance
				map.put(minAss, min);
			}
			
		Set<Entry<String, Double>> set = map.entrySet();
	    List<Entry<String, Double>> list = new ArrayList<Entry<String, Double>>(set);
	
	    Collections.sort( list, new Comparator<Entry<String, Double>>()
	            {
	                public int compare( Entry<String, Double> o1, Entry<String, Double> o2 )
	                {
	                    return (o1.getValue()).compareTo( o2.getValue() );
	                }
	            } );
			
	    
	    System.out.println("###############################");
	    for(Entry<String,Double> in : list)
		{
			System.out.println(in.getKey() +"--"+in.getValue());
		}
	    System.out.println("###############################");
	    
	    
		List<String> output = new ArrayList<String>();
		
		int c = 0;
		for(Entry<String,Double> in : list)
		{
			output.add(in.getKey());
			
			if(c == 4)
				break;
			
			c++;
		}	
		
		return output;
	}
		
		private class MiscellaneousUtils
		{
			public List<String> removeStopWords(String string) throws IOException 
		    {
		        StandardAnalyzer ana = new StandardAnalyzer(Version.LUCENE_30);
		        
		        CharArraySet stopSet = CharArraySet.copy(Version.LUCENE_36,
		        		StandardAnalyzer.STOP_WORDS_SET);
		        
		        
		        InputStream is = Recommender.class.getClassLoader().getResourceAsStream("stop_words.txt");
		        Reader r = new InputStreamReader(is);
		        BufferedReader file = new BufferedReader(r);
		        
		        String line;

		        while((line = file.readLine()) != null)
		        {
		         stopSet.add(line);	
		        }
		        
		        file.close();
		        
		        TokenStream tokenStream = new StandardTokenizer(Version.LUCENE_36,new StringReader(string));
		        
		        tokenStream = new StopFilter(Version.LUCENE_36, tokenStream, stopSet);
		        
		        List<String> lista = new ArrayList<String>();
		        tokenStream = new StopFilter(Version.LUCENE_36, tokenStream, StandardAnalyzer.STOP_WORDS_SET);
		        CharTermAttribute token = tokenStream.getAttribute(CharTermAttribute.class);
		        while (tokenStream.incrementToken()) 
		        {
		           lista.add(token.toString());
		        }
		        
		        tokenStream.close();
		        ana.close();
		        
		        return lista;
		    }

			public BufferedReader readDataFile(String filename)
		    {
			        BufferedReader inputReader = null;

				        try
				        {
				            inputReader = new BufferedReader(new FileReader(filename));
				        }
				        catch (FileNotFoundException ex)
				        {
				        }

			        return inputReader;
		    }  
			
			public void serializeRulesOnFile(String fn,AssociationRules arules) throws IOException
			{

		        OutputStream file = new FileOutputStream(fn);
		        OutputStream buffer = new BufferedOutputStream(file);
		        ObjectOutput output = new ObjectOutputStream(buffer);
		      
		        output.writeObject(arules);
		        
		        output.close();
			}
			
			public void serializeTagsOnFile(String fn,List<String> lista) throws IOException
			{

		        OutputStream file = new FileOutputStream(fn);
		        OutputStream buffer = new BufferedOutputStream(file);
		        ObjectOutput output = new ObjectOutputStream(buffer);
		      
		        output.writeObject(lista);
		        
		        output.close();
			}
			
			public AssociationRules deserializeRulesOnFile(String fn) throws IOException, ClassNotFoundException
			{
				 
		        InputStream nfile = new FileInputStream(fn);
		        InputStream nbuffer = new BufferedInputStream(nfile);
		        ObjectInput input = new ObjectInputStream(nbuffer);
		      
		        AssociationRules readRules = (AssociationRules) input.readObject();
		       
		        input.close();
		        
		        return readRules;
			}
			
			
			public List<String> deserializeTagOnFile(String fn) throws IOException, ClassNotFoundException
			{
				 
		        InputStream nfile = new FileInputStream(fn);
		        InputStream nbuffer = new BufferedInputStream(nfile);
		        ObjectInput input = new ObjectInputStream(nbuffer);
		      
		        @SuppressWarnings("unchecked")
				List<String> tags = (List<String>) input.readObject();
		       
		        input.close();
		        
		        return tags;
			}
		}
}