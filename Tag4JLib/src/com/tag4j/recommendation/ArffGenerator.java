package com.tag4j.recommendation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * An arff file generator object can be used to generate an .arrf file
 * based on a list of transactions.
 * 
 * @author RaffaeleEsposito
 *
 */

public class ArffGenerator
{
    private int tagCounter;
    private int transactionCounter;
    private List<String> allTheTags;
    private File outFile;
    
    private String NOME_ARFF_FILE = "my_arff_file";

    /**
     * 
     * 
     * @param tagsets transaction lists
     *                it cannot be null or zero size
     * @param path  the pathname of the file generated, if null
     *              the file will be generated in the current directory
     * @throws IOException
     */
    public ArffGenerator(List<List<String>> tagsets,String path) throws IOException
    {
    	if(tagsets == null || tagsets.size() == 0)
    		throw new IllegalArgumentException("");
    	
    	transactionCounter = tagsets.size();
    	    	
    	allTheTags = new ArrayList<String>();	
    
    	for(List<String> in : tagsets)
    	{    		
    	
    		for(String s : in)
    		{
    			if(notPresentIn(allTheTags,s))
    				allTheTags.add(s);
    		}
    	}

    	tagCounter = allTheTags.size();
    	
    	
    	if(path == null)
        	outFile = new File(NOME_ARFF_FILE);
        else
        	outFile = new File(path + java.io.File.separator + NOME_ARFF_FILE);	
    	
		PrintWriter writer = new PrintWriter(outFile, "UTF-8");
				
		writer.println("@relation Presence-Absence-Representation");
		writer.println();
		
		for(String s: allTheTags)
		{
			writer.println("@attribute " + s + " {0,1}");
		}
		writer.println();
		writer.println("@data");
    	
     	
     	for(List<String> in : tagsets)
    	{
    	
     		ArrayList<String> myLine = new ArrayList<String>();

    		for(String s : in)
    				myLine.add(s);
    		
    		ArffFileRow arf = new ArffFileRow(allTheTags);
    		arf.setToConvert(myLine);
    		writer.println(arf.toString());
    	}
     	
     	writer.close();
    }
       
    /**
     * 
     * @return
     */
	public File getOutFile() {
		return outFile;
	}
	
	/**
	 * 
	 * @return
	 */
	public List<String> getAllTheTags() {
		return allTheTags;
	}

	/**
	 * 
	 * @return number of transactions
	 */
    public int getTransactionCounter() {
		return transactionCounter;
	}


	/**
	 * 
	 * @return number of tags
	 */
	public int getTagCounter() {
		return tagCounter;
	}


	private boolean notPresentIn(List<String> lista,String s)
    {
    	for(String word : lista)
    	{
    		
    		if(word.equalsIgnoreCase(s))
    			return false;
    		
    	}
    	
    	return true;
    }
    
    private class ArffFileRow 
    {
    	private List<String> allTheTags;
    	private List<String> toConvert;
    	
    	public ArffFileRow(List<String> allTheTags)
    	{
    		this.allTheTags = allTheTags;
    	}
    	
    	public void setToConvert(List<String> toConvert)
    	{
    		this.toConvert = toConvert;
    	}
    	
    	public String toString()
    	{
    		String toRet = "";
    		
    		for(String tag : allTheTags)
    		{
    			boolean b = false;
    			for(String nTag : toConvert)
    			{
    				if(nTag.equalsIgnoreCase(tag))
    				{
    					b = true;
    					break;
    				}	
    				
    			}
    			
    			if(b)
    				toRet = toRet + "1,";
    			else	
    				toRet = toRet + "0,";		
    		}
    		
    		return toRet.substring(0, toRet.length() - 1);
    	}
    }
}