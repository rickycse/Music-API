package com.eecs3311.profilemicroservice;

import okhttp3.*;
import org.json.JSONObject;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;

import java.util.*;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	OkHttpClient client = new OkHttpClient();

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Playlist constraint already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		DbQueryStatus status;

		try {
			Session session = driver.session();
			// Sends a GET request to check if the Song exists
			try {
				StatementResult result = driver.session().run(String.format("MATCH (s:song {songId:'%s'}) RETURN s", songId));
				// Since the Song doesn't exist in Neo4j, checks if the Song exists in MongoDB
				if (!result.hasNext()) {
					String getUrl = "http://localhost:3001/getSongById/" + songId;
					Request getRequest = new Request.Builder()
							.url(getUrl)
							.build();
					try {
						Response response = client.newCall(getRequest).execute();
						if(!response.isSuccessful()){
							return new DbQueryStatus("1. Failed GET request to " + getUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
						}
						// Since the Song does exist in MongoDB, create the Song in Neo4j.
						driver.session().run(String.format("CREATE (s:song {songId: '%s'})", songId));
					} catch (Exception e) {
						return new DbQueryStatus("2. Failed GET request to " + getUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
				} // Otherwise, the Song already exists in both MongoDB and Neo4j
			} catch (Exception e) {
				return new DbQueryStatus("Failed adding new Song to database", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
			// Checks if the user has liked the song before to prevent duplicate liking.
			StatementResult hasLikedBefore = driver.session().run(String.format("MATCH (:playlist {userName: '%s-favorites'})-[r:includes]->(:song {songId:'%s'}) RETURN r", userName, songId));
			if(hasLikedBefore.hasNext()){
				// Sends a POST request to update Song favorites count
				String postUrl = "http://localhost:3001/updateSongFavouritesCount";
				String payload = String.format("{\"songId\": \"%s\", \"shouldDecrement\": \"false\"}", songId);
				MediaType JSON = MediaType.get("application/json; charset=utf-8");
				RequestBody body = RequestBody.create(payload, JSON);
				Request postRequest = new Request.Builder()
						.url(postUrl)
						.put(body)
						.build();
				try {
					Response response = client.newCall(postRequest).execute();
					if(!response.isSuccessful()){
						return new DbQueryStatus("1. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					} else {
						response.close();
					}
				} catch (Exception e) {
					return new DbQueryStatus("2. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}
			}
			// Creates an includes relationship between the user's playlist and song (Using MERGE to prevent duplicate relationships).
			session.run(String.format("MATCH (p:playlist {plName: '%s-favorites'}), (s:song {songId: '%s'}) MERGE (p)-[:includes]->(s)", userName, songId));
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			e.printStackTrace();
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}

	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		DbQueryStatus status;
		try {
			Session session = driver.session();
			// Sends a POST request to decrement the Song's favorites
			String postUrl = "http://localhost:3001/updateSongFavouritesCount";
			MediaType JSON = MediaType.get("application/json; charset=utf-8");
			String payload = String.format("{\"songId\": \"%s\", \"shouldDecrement\": \"true\"}", songId);
			RequestBody body = RequestBody.create(payload, JSON);
			Request postRequest = new Request.Builder()
					.url(postUrl)
					.put(body)
					.build();
			try (Response response = client.newCall(postRequest).execute()){
				if(!response.isSuccessful()){
					return new DbQueryStatus("1. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}
			} catch (Exception e) {
				return new DbQueryStatus("2. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
			// Deletes the includes relationship between the user's playlist and song.
			session.run(String.format("MATCH (p:playlist {plName: '%s-favorites'})-[r:includes]->(s:song {songId: '%s'}) DELETE r", userName, songId));
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}

	@Override
	public DbQueryStatus generateMixedPlaylist(String userName, String friendUserName) {
		ArrayList<Object> userSongs = new ArrayList<>();
		ArrayList<Object> friendSongs = new ArrayList<>();
		DbQueryStatus status = new DbQueryStatus("", DbQueryExecResult.QUERY_OK);
		// Finds the playlists of both users, combines, and shuffles the playlists.
		try {
			String userQuery = String.format("MATCH (:playlist {plName: '%s-favorites'})-[:includes]->(s:song) RETURN s", userName);
			StatementResult userPlaylist = driver.session().run(userQuery);
			// Gets all the user's songs
			while (userPlaylist.hasNext()) {
				org.neo4j.driver.v1.Record record = userPlaylist.next();
				Map<String, Object> map = record.get(0).asMap();
				userSongs.add(map.get("songId"));
			}
			// Gets all the user's friend's songs.
			String friendQuery = String.format("MATCH (:playlist {plName: '%s-favorites'})-[:includes]->(s:song) RETURN s", friendUserName);
			StatementResult friendPlaylist = driver.session().run(friendQuery);
			while (friendPlaylist.hasNext()) {
				org.neo4j.driver.v1.Record record = friendPlaylist.next();
				Map<String, Object> map = record.get(0).asMap();
				friendSongs.add(map.get("songId"));
			}
			// Adds and shuffles the combined playlist
			userSongs.addAll(friendSongs);
			Collections.shuffle(userSongs);
		} catch (Exception e){
			e.printStackTrace();
			return new DbQueryStatus("Error with driver session", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		// Gets all the song titles from the two playlists
		ArrayList<String> songTitles = new ArrayList<>();
		for(Object id : userSongs){
			String songUrl = "http://localhost:3001/getSongTitleById/" + id;
			Request request = new Request.Builder().url(songUrl).build();
			try {
				Response response = client.newCall(request).execute();
				if (!response.isSuccessful()) return new DbQueryStatus("Error with driver session", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				JSONObject json = new JSONObject(Objects.requireNonNull(response.body()).string());
				songTitles.add((String) json.get("data"));
			} catch (Exception e) {
				e.printStackTrace();
				return new DbQueryStatus("Error requesting song title with ID: " + id, DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		}
		// Returns a list of Song titles from the two playlists
		status.setData(songTitles);
		status.setMessage("Success");
		return status;
	}
}
