package com.eecs3311.songmicroservice;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
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

	@Autowired
	public SongDalImpl(MongoTemplate mongoTemplate) {
		this.db = mongoTemplate;
	}

	@Override
	public DbQueryStatus addSong(Song songToAdd) {
		// TODO Auto-generated method stub
		DbQueryExecResult execResult;
		String response;
		Song data = songToAdd;

		try {
			db.insert(songToAdd);
			response = String.format("Successfully added Song %s to database", songToAdd.getSongName());
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e){
			response = String.format("Error %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}

		DbQueryStatus status = new DbQueryStatus(response, execResult);
		status.setData(data);
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
			response = String.format("Removed Song with ID %s from database", songId);
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e){
			response = String.format("Error %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}
		System.out.println(response);
		DbQueryStatus status = new DbQueryStatus(response, execResult);
		return status;
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