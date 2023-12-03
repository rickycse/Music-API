package com.eecs3311.songmicroservice;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

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
		System.out.println(songToAdd.toString());

		try {
			db.insert(songToAdd);
			response = String.format("Successfully added Song %s to database", songToAdd.getSongName());
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e){
			response = String.format("Error %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}

		DbQueryStatus status = new DbQueryStatus(response, execResult);
		return status;
	}

	@Override
	public DbQueryStatus findSongById(String songId) {
		// TODO Auto-generated method stub
		DbQueryExecResult execResult;
		String response;
		Song data;

		try {
			data = db.findById(songId, Song.class);
			response = String.format("Song: %s retrieved successfully.", data.getSongName());
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e) {
			data = null;
			response = String.format("Error retrieving Song with ID: %s", songId);
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
			db.updateFirst(query, update, Song.class);
			response = String.format("Removed Song with ID %s from database", songId);
			execResult = DbQueryExecResult.QUERY_OK;
		} catch (Exception e){
			response = String.format("Error %s", e);
			execResult = DbQueryExecResult.QUERY_ERROR_GENERIC;
		}

		DbQueryStatus status = new DbQueryStatus(response, execResult);
		return status;
	}
}