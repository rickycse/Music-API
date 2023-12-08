package com.eecs3311.songmicroservice;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class SongDalImpl implements SongDal {

	private final MongoTemplate db;

	private OkHttpClient client = new OkHttpClient();

	@Autowired
	public SongDalImpl(MongoTemplate mongoTemplate) {
		this.db = mongoTemplate;
	}

	@Override
	public DbQueryStatus addSong(Song songToAdd) {
		// TODO Auto-generated method stub
		DbQueryExecResult execResult;
		String response;

		try {
			// Adds the Song to MongoDB
			db.insert(songToAdd);
			// Sends a POST request to add the Song in Neo4j DB
			String postUrl = "http://localhost:3002/addSongNode/" + songToAdd.getId();
			RequestBody empty = RequestBody.create("", null);
			Request postRequest = new Request.Builder().url(postUrl).post(empty).build();
			try {
				Response r = client.newCall(postRequest).execute();
				if(!r.isSuccessful()){
					return new DbQueryStatus("1. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				} else {
					r.close();
				}
			} catch (Exception e) {
				return new DbQueryStatus("2. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
			response = String.format("Successfully added Song %s to MongoDB and Neo4j", songToAdd.getSongName());
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e){
			response = String.format("Error %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}

		DbQueryStatus status = new DbQueryStatus(response, execResult);
		status.setData(songToAdd);
		return status;
	}

	@Override
	public DbQueryStatus findSongById(String songId) {
		// TODO Auto-generated method stub
		Query query = new Query(Criteria.where("_id").is(songId));
		DbQueryExecResult execResult;
		String response;
		Song data;

		try {
			data = db.findOne(query, Song.class);
			if(data == null) return new DbQueryStatus(String.format("Song with ID %s does not exist.", songId), DbQueryExecResult.QUERY_OK);
			response = String.format("Song: %s retrieved successfully.", data.getSongName());
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e) {
			data = null;
			response = String.format("Error %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}

		DbQueryStatus status = new DbQueryStatus(response, execResult);
		status.setData(data);
		return status;
	}

	@Override
	public DbQueryStatus getSongTitleById(String songId) {
		// TODO Auto-generated method stub
		Query query = new Query(Criteria.where("_id").is(songId));
		query.fields().include("songName").exclude("_id");
		DbQueryExecResult execResult;
		String response;
		Song data;

		try {
			data = db.findOne(query, Song.class);
			response = String.format("Song name from ID: %s retrieved successfully.", songId);
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e) {
			data = null;
			response = String.format("Error %s.", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}
		System.out.println(response);
		DbQueryStatus status = new DbQueryStatus(response, execResult);
		if (data != null) status.setData(data.getSongName());
		return status;
	}

	@Override
	public DbQueryStatus deleteSongById(String songId) {
		// TODO Auto-generated method stub
		Query query = new Query(Criteria.where("_id").is(songId));
		DbQueryExecResult execResult;
		String response;

		try {
			db.remove(query, Song.class);
			try {
				String deleteUrl = "http://localhost:3002/deleteSongNode/" + songId;
				RequestBody empty = RequestBody.create("", null);
				Request deleteRequest = new Request.Builder().url(deleteUrl).delete(empty).build();
				try {
					Response r = client.newCall(deleteRequest).execute();
					if(!r.isSuccessful()){
						return new DbQueryStatus("1. Failed DELETE request to " + deleteUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					} else {
						r.close();
					}
				} catch (Exception e) {
					return new DbQueryStatus("2. Failed DELETE request to " + deleteUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}
				response = String.format("Deleted Song with ID '%s' from MongoDB and Neo4j", songId);
				execResult = DbQueryExecResult.QUERY_OK;
			} catch (Exception e){
				response = String.format("Error deleting from MongoDB.\n%s", e);
				execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
			}
		} catch (Exception e){
			response = String.format("Error: %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}
		return new DbQueryStatus(response, execResult);
	}

	@Override
	public DbQueryStatus updateSongFavouritesCount(String songId, boolean shouldDecrement) {
		// TODO Auto-generated method stub
		Query query = new Query(Criteria.where("_id").is(songId));
		int incrementBy = shouldDecrement ? -1 : 1;
		Update update = new Update().inc("songAmountFavourites", incrementBy);
		DbQueryExecResult execResult;
		String response;

		try {
			Query playCheck = new Query(Criteria.where("_id").is(songId));
			playCheck.fields().include("songAmountFavourites").exclude("_id");
			Song song = db.findOne(playCheck, Song.class);

			// If the songAmountFavourites is 0 and the user wants to decrement, do nothing.
			if(song != null && song.getSongAmountFavourites() == 0 && shouldDecrement){}
			else {
				db.updateFirst(query, update, Song.class);
			}

			response = String.format("Removed Song with ID %s from database", songId);
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e){
			response = String.format("Error %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}

		DbQueryStatus status = new DbQueryStatus(response, execResult);
		return status;
	}

	@Override
	public DbQueryStatus generateRandomPlaylist(int length) {
		//
		int lengthInSec = length * 60;
		int totalSongDurCounter = 0;

		try {
			List<Song> randomSongs = new ArrayList<>();

			// get all songs from the database
			List<Song> allSongs = db.findAll(Song.class);

			// randomize the order of the songs
			Collections.shuffle(allSongs);

			for (int i = 0; i < allSongs.size(); i++) {
				if (totalSongDurCounter + allSongs.get(i).getSongDuration() <= lengthInSec) {
					// Adding the song at index 'i' will still be less than 'lengthInSec', so add it
					randomSongs.add(allSongs.get(i));

					// adding random song's duration to total count
					totalSongDurCounter += allSongs.get(i).getSongDuration();
				}
			}

			DbQueryStatus dbQueryStat = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
			dbQueryStat.setData(randomSongs);
			return dbQueryStat;
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
}