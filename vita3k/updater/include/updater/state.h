// Vita3K emulator project
// Copyright (C) 2026 Vita3K team
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace updater {

enum class UpdateCheckMode {
    ManualInteractive,
    StartupPrompt,
    StartupBackground,
};

enum class UpdateCheckStatus {
    Failed,
    UpToDate,
    UpdateAvailable,
    CurrentBuildNewerThanLatest,
    CustomBuildCanUpdate,
};

struct ChangelogEntry {
    std::string version;
    std::string title;
};

struct UpdateInfo {
    std::string version;
    std::uint64_t build_number = 0;
    std::string release_url;
    std::string published_at;
    std::string notes;
    std::vector<ChangelogEntry> changelog;
};

struct UpdateCheckResult {
    UpdateCheckStatus status = UpdateCheckStatus::Failed;
    std::string message;
    UpdateInfo info;
    std::string current_display_version;
};

} // namespace updater
