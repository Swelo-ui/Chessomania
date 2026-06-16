package com.chessomania.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chessomania.app.R
import com.chessomania.app.net.ApiResult
import com.chessomania.app.net.FriendInfo
import com.chessomania.app.net.NetworkClient
import com.chessomania.app.net.SseEvent
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class FriendsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var editAddFriend: EditText
    private lateinit var btnAddFriend: Button
    
    private lateinit var labelRequests: TextView
    private lateinit var recyclerRequests: RecyclerView
    
    private lateinit var labelChallenges: TextView
    private lateinit var recyclerChallenges: RecyclerView
    
    private lateinit var recyclerFriends: RecyclerView

    private var friendsList = mutableListOf<FriendInfo>()
    private var pendingRequests = mutableListOf<String>() // Usernames of incoming requests
    private var incomingChallenges = mutableListOf<SseEvent>()

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var requestsAdapter: RequestsAdapter
    private lateinit var challengesAdapter: ChallengesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_friends, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editAddFriend = view.findViewById(R.id.edit_add_friend_username)
        btnAddFriend = view.findViewById(R.id.btn_add_friend)
        
        labelRequests = view.findViewById(R.id.label_friend_requests)
        recyclerRequests = view.findViewById(R.id.recycler_friend_requests)
        
        labelChallenges = view.findViewById(R.id.label_challenges)
        recyclerChallenges = view.findViewById(R.id.recycler_challenges)
        
        recyclerFriends = view.findViewById(R.id.recycler_friends)

        // Setup Layout Managers
        recyclerRequests.layoutManager = LinearLayoutManager(requireContext())
        recyclerChallenges.layoutManager = LinearLayoutManager(requireContext())
        recyclerFriends.layoutManager = LinearLayoutManager(requireContext())

        // Setup Adapters
        requestsAdapter = RequestsAdapter()
        recyclerRequests.adapter = requestsAdapter

        challengesAdapter = ChallengesAdapter()
        recyclerChallenges.adapter = challengesAdapter

        friendsAdapter = FriendsAdapter()
        recyclerFriends.adapter = friendsAdapter

        // Load challenges from parent PlayFragment
        val playFragment = parentFragment as? PlayFragment
        if (playFragment != null) {
            incomingChallenges.addAll(playFragment.getIncomingChallenges())
        }
        updateChallengesUI()

        // Button Click to Add Friend
        btnAddFriend.setOnClickListener {
            val username = editAddFriend.text.toString().trim()
            if (username.isNotEmpty()) {
                sendFriendRequest(username)
            } else {
                Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        // Fetch Data
        refreshData()
    }

    private fun refreshData() {
        lifecycleScope.launch {
            fetchFriendsList()
            fetchPendingRequests()
        }
    }

    private suspend fun fetchFriendsList() {
        val result = NetworkClient.get(requireContext(), "/api/friends/list")
        if (result is ApiResult.Success) {
            val list = result.data["friends"] as? List<Map<String, Any?>>
            friendsList.clear()
            list?.forEach { item ->
                val username = item["username"] as? String
                val status = item["status"] as? String
                if (username != null && status != null) {
                    friendsList.add(FriendInfo(username, status))
                }
            }
            friendsAdapter.notifyDataSetChanged()
        }
    }

    private suspend fun fetchPendingRequests() {
        val result = NetworkClient.get(requireContext(), "/api/friends/pending")
        if (result is ApiResult.Success) {
            val list = result.data["pending"] as? List<Map<String, Any?>>
            pendingRequests.clear()
            list?.forEach { item ->
                val username = item["username"] as? String
                if (username != null) {
                    pendingRequests.add(username)
                }
            }
            updateRequestsUI()
        }
    }

    private fun updateRequestsUI() {
        if (pendingRequests.isEmpty()) {
            labelRequests.visibility = View.GONE
            recyclerRequests.visibility = View.GONE
        } else {
            labelRequests.visibility = View.VISIBLE
            recyclerRequests.visibility = View.VISIBLE
            requestsAdapter.notifyDataSetChanged()
        }
    }

    private fun updateChallengesUI() {
        if (incomingChallenges.isEmpty()) {
            labelChallenges.visibility = View.GONE
            recyclerChallenges.visibility = View.GONE
        } else {
            labelChallenges.visibility = View.VISIBLE
            recyclerChallenges.visibility = View.VISIBLE
            challengesAdapter.notifyDataSetChanged()
        }
    }

    private fun sendFriendRequest(targetUsername: String) {
        btnAddFriend.isEnabled = false
        lifecycleScope.launch {
            val result = NetworkClient.post(
                requireContext(),
                "/api/friends/request",
                mapOf("targetUsername" to targetUsername)
            )
            btnAddFriend.isEnabled = true
            if (result is ApiResult.Success) {
                Toast.makeText(context, "Friend request sent to $targetUsername", Toast.LENGTH_SHORT).show()
                editAddFriend.text.clear()
                refreshData()
            } else if (result is ApiResult.Error) {
                Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun respondFriendRequest(fromUsername: String, accept: Boolean) {
        lifecycleScope.launch {
            val result = NetworkClient.post(
                requireContext(),
                "/api/friends/respond",
                mapOf("fromUsername" to fromUsername, "accept" to accept)
            )
            if (result is ApiResult.Success) {
                val action = if (accept) "accepted" else "declined"
                Toast.makeText(context, "Friend request $action", Toast.LENGTH_SHORT).show()
                refreshData()
            } else if (result is ApiResult.Error) {
                Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun respondChallenge(challengeId: String, accept: Boolean) {
        lifecycleScope.launch {
            val result = NetworkClient.post(
                requireContext(),
                "/api/challenge/respond",
                mapOf("challengeId" to challengeId, "accept" to accept)
            )
            if (result is ApiResult.Success) {
                // If accepted, the game will start via game_start event.
                // Let's remove from local list and dismiss bottom sheet.
                incomingChallenges.removeAll { it.challengeId == challengeId }
                val playFragment = parentFragment as? PlayFragment
                playFragment?.removeChallenge(challengeId)
                
                if (accept) {
                    dismiss()
                } else {
                    updateChallengesUI()
                    Toast.makeText(context, "Challenge declined", Toast.LENGTH_SHORT).show()
                }
            } else if (result is ApiResult.Error) {
                Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendChallenge(targetUsername: String) {
        lifecycleScope.launch {
            val result = NetworkClient.post(
                requireContext(),
                "/api/challenge/send",
                mapOf("targetUsername" to targetUsername, "color" to "random")
            )
            if (result is ApiResult.Success) {
                Toast.makeText(context, "Challenge sent to $targetUsername!", Toast.LENGTH_SHORT).show()
            } else if (result is ApiResult.Error) {
                Toast.makeText(context, "Failed to challenge: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Adapters ─────────────────────────────────────────────────────────

    inner class RequestsAdapter : RecyclerView.Adapter<RequestsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val txtUser: TextView = v.findViewById(R.id.text_username)
            val btnAccept: Button = v.findViewById(R.id.btn_accept)
            val btnDecline: Button = v.findViewById(R.id.btn_decline)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_request, parent, false)
            return VH(v)
        }

        override fun getItemCount() = pendingRequests.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = pendingRequests[position]
            holder.txtUser.text = user
            holder.btnAccept.setOnClickListener { respondFriendRequest(user, true) }
            holder.btnDecline.setOnClickListener { respondFriendRequest(user, false) }
        }
    }

    inner class ChallengesAdapter : RecyclerView.Adapter<ChallengesAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val txtUser: TextView = v.findViewById(R.id.text_username)
            val btnAccept: Button = v.findViewById(R.id.btn_accept)
            val btnDecline: Button = v.findViewById(R.id.btn_decline)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_challenge, parent, false)
            return VH(v)
        }

        override fun getItemCount() = incomingChallenges.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val challenge = incomingChallenges[position]
            holder.txtUser.text = "${challenge.from} challenged you!"
            holder.btnAccept.setOnClickListener {
                challenge.challengeId?.let { id -> respondChallenge(id, true) }
            }
            holder.btnDecline.setOnClickListener {
                challenge.challengeId?.let { id -> respondChallenge(id, false) }
            }
        }
    }

    inner class FriendsAdapter : RecyclerView.Adapter<FriendsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val dot: View = v.findViewById(R.id.status_dot)
            val txtUser: TextView = v.findViewById(R.id.text_username)
            val txtStatus: TextView = v.findViewById(R.id.text_status)
            val btnAction: Button = v.findViewById(R.id.btn_action)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
            return VH(v)
        }

        override fun getItemCount() = friendsList.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val friend = friendsList[position]
            holder.txtUser.text = friend.username
            holder.txtStatus.text = friend.status

            val dotColor = when (friend.status) {
                "online" -> ContextCompat.getColor(requireContext(), R.color.green)
                "in_game" -> ContextCompat.getColor(requireContext(), R.color.accent)
                else -> ContextCompat.getColor(requireContext(), R.color.text_secondary)
            }
            holder.dot.background.setTint(dotColor)

            if (friend.status == "online") {
                holder.btnAction.visibility = View.VISIBLE
                holder.btnAction.text = "Challenge"
                holder.btnAction.isEnabled = true
                holder.btnAction.setOnClickListener {
                    sendChallenge(friend.username)
                }
            } else if (friend.status == "in_game") {
                holder.btnAction.visibility = View.VISIBLE
                holder.btnAction.text = "In Game"
                holder.btnAction.isEnabled = false
            } else {
                holder.btnAction.visibility = View.GONE
            }
        }
    }
}
