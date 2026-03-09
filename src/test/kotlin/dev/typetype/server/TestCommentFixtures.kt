package dev.typetype.server

import dev.typetype.server.models.CommentItem

fun testCommentItem(
    replyCount: Int = -1,
    repliesPage: String? = null,
    textualLikeCount: String = "",
    uploaderVerified: Boolean = false,
): CommentItem = CommentItem(
    id = "comment-id",
    text = "Test comment",
    author = "Author",
    authorUrl = "https://youtube.com/channel/test",
    authorAvatarUrl = "",
    likeCount = 10L,
    textualLikeCount = textualLikeCount,
    publishedTime = "2 days ago",
    isHeartedByUploader = false,
    isPinned = false,
    uploaderVerified = uploaderVerified,
    replyCount = replyCount,
    repliesPage = repliesPage,
)
