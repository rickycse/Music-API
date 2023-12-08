package com.eecs3311.profilemicroservice;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.json.JSONObject;
import org.neo4j.driver.v1.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {
	// Initialize the Neo4j driver from the ProfileMicroserviceApplication.
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
	/**
	 * Method to like a song. It creates a relationship between a User and a Song node in the Neo4j database.
	 * @param playlistId The username of the user liking the song.
	 * @param songId The ID of the song being liked.
	 * @return DbQueryStatus The status of the database query including any error or success messages.
	 */
	@Override
	public DbQueryStatus likeSong(String playlistId, String songId) {
		// Initialize the query status with a default success message.
		DbQueryStatus dbQueryStatus = new DbQueryStatus("Like Song", DbQueryExecResult.QUERY_OK);

		/*//  Check if the song exists in MongoDB
		String songUrl = "http://localhost:3001/getSongById/" + songId;
		Request request = new Request.Builder().url(songUrl).build();
		try {
			Response response = client.newCall(request).execute();
			if (!response.isSuccessful()) {
				return new DbQueryStatus("Song not found in MongoDB", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new DbQueryStatus("Error checking song in MongoDB", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}*/

		try (Session session = driver.session()) {
			// Step 2: Create the song in Neo4j if it exists and create a relationship
			String query = "MATCH (s:Song { songArtistFullName: \"" + playlistId + "\" }) " +
					"WHERE id(s) = " + songId +
					" SET s.songAmountFavourites = s.songAmountFavourites + 1 " +
					"RETURN s";

			StatementResult result = session.run(query, Values.parameters("playlistId", playlistId, "songId", songId));

			if (!result.hasNext()) {
				dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				dbQueryStatus.setMessage("Playlist not found");
			} else {
				dbQueryStatus.setMessage("Song added to playlist successfully");
			}
		} catch (Exception e) {
			dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_GENERIC);
			dbQueryStatus.setData(e.getMessage());
		}
		String updateUrl = "http://localhost:3001/updateSongFavoritesCount/" + songId;
		MediaType mediaType = MediaType.parse("application/json"); // or the appropriate media type
		RequestBody body = RequestBody.create("", mediaType); // Empty body for PUT request
		Request updateRequest = new Request.Builder().url(updateUrl).put(body).build();
		try {
			Response updateResponse = client.newCall(updateRequest).execute();
			if (!updateResponse.isSuccessful()) {
				return new DbQueryStatus("Error updating songFavoritesCount", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new DbQueryStatus("Error sending PUT request to update songFavoritesCount", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		return dbQueryStatus;
	}


	/**
	 * Method to unlike a song. It deletes a relationship between a User and a Song node in the Neo4j database.
	 * @param playlistId The username of the user unliking the song.
	 * @param songId The ID of the song being unliked.
	 * @return DbQueryStatus The status of the database query including any error or success messages.
	 */
	@Override
	public DbQueryStatus unlikeSong(String playlistId, String songId) {
		// Initialize the query status with a default success message.
		DbQueryStatus dbQueryStatus = new DbQueryStatus("Unlike Song", DbQueryExecResult.QUERY_OK);

		try (Session session = driver.session()) {
			// Step 1: Verify the song exists in Neo4j


			String query = "MATCH (s:Song { songArtistFullName: \"" + playlistId + "\" }) " +
					"WHERE id(s) = " + songId +
					" SET s.songAmountFavourites = s.songAmountFavourites - 1 " +
					"RETURN s";


			StatementResult result = session.run(query, Values.parameters("playlistId", playlistId, "songId", songId));

			if (!result.hasNext()) {
				dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				dbQueryStatus.setMessage("Relationship between playlist and song not found");
				return dbQueryStatus;
			}
			String deleteQuery = "MATCH (p:Playlist {id: $playlistId})-[r:CONTAINS]->(s:Song {id: $songId}) " +
					"DELETE r";
			session.run(deleteQuery, Values.parameters("playlistId", playlistId, "songId", songId));
			dbQueryStatus.setMessage("Song removed from playlist successfully");

			// Step 3: Send a PUT request to update songFavoritesCount
			String updateUrl = "http://localhost:3001/updateSongFavoritesCount/" + songId;
			MediaType mediaType = MediaType.parse("application/json"); // or the appropriate media type
			RequestBody body = RequestBody.create("", mediaType); // Empty body for PUT request
			Request updateRequest = new Request.Builder().url(updateUrl).put(body).build();
			try {
				Response updateResponse = client.newCall(updateRequest).execute();
				if (!updateResponse.isSuccessful()) {
					return new DbQueryStatus("Error updating songFavoritesCount", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			} catch (Exception e) {
				e.printStackTrace();
				return new DbQueryStatus("Error sending PUT request to update songFavoritesCount", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		} catch (Exception e) {
			dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_GENERIC);
			dbQueryStatus.setData(e.getMessage());
		}

			return dbQueryStatus;

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
