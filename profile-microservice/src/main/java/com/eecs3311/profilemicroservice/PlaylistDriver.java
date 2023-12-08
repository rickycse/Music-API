package com.eecs3311.profilemicroservice;

public interface PlaylistDriver {
	DbQueryStatus addSongNode(String songId);
	DbQueryStatus deleteSongNode(String songId);
	DbQueryStatus likeSong(String userName, String songId);
	DbQueryStatus unlikeSong(String userName, String songId);
	DbQueryStatus generateMixedPlaylist(String userName, String friendUserName);
}