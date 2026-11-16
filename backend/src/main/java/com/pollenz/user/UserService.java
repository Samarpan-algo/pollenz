package com.pollenz.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pollenz.common.ApiException;

@Service
public class UserService {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public UserResponse get(long userId) {
		return UserResponse.from(load(userId));
	}

	@Transactional
	public UserResponse update(long userId, UpdateProfileRequest req) {
		User user = load(userId);
		if (req.name() != null) {
			user.setName(req.name().trim());
		}
		if (req.upiId() != null) {
			String upi = req.upiId().trim();
			user.setUpiId(upi.isEmpty() ? null : upi);
		}
		return UserResponse.from(user);
	}

	private User load(long userId) {
		// A valid token for a user that no longer exists is treated as unauthenticated.
		return users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User no longer exists"));
	}

}
