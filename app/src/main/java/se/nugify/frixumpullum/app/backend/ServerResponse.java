package se.nugify.frixumpullum.app.backend;

import java.util.Arrays;

/**
 * Created by tomas on 2/26/17.
 *
 */

public class ServerResponse {

    private String[] words;
    /**
     * List of probabiliteis for each specific word.
     * int since its less prone for errors. interneal requirement could be that this must be between
     * 0 and 10000 where 10000 = 100%. This gives space for some decimal points.
     */
    private int[] probabilities;

    public ServerResponse(String[] words, int[] probabilities){
        this.words = words;
        this.probabilities = probabilities;

    }

    /**
     * Gives a copy of the words in the response
     * @return
     */
    public String[] getWords(){
        return words.clone();
    }

    /**
     * Geives a copy of the probabilites in the response
     * @return
     */
    public int[] getProbabilities(){
        return probabilities.clone();
    }

}
