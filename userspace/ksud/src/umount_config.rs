use std::collections::HashMap;
use std::fs;
use std::path::Path;

use anyhow::Result;
use log::info;
use serde_json::Value;

use crate::defs;
use crate::ksucalls;

const UMOUNT_CONFIG_PATH: &str = defs::UMOUNT_CONFIG_PATH;

#[derive(Default)]
struct Config {
    paths: HashMap<String, u32>,
}

fn read_config() -> Config {
    let Ok(content) = fs::read_to_string(UMOUNT_CONFIG_PATH) else {
        return Config::default();
    };

    let value: Value = serde_json::from_str(&content).unwrap_or(Value::Null);
    let mut paths = HashMap::new();

    if let Some(entries) = value.get("paths").and_then(Value::as_object) {
        for (path, flags) in entries {
            if let Some(flags) = flags.as_u64() {
                paths.insert(path.clone(), flags as u32);
            }
        }
    }

    Config { paths }
}

fn write_config(config: &Config) -> Result<()> {
    let value = serde_json::json!({ "paths": config.paths });
    let content = serde_json::to_string_pretty(&value)?;

    if let Some(parent) = Path::new(UMOUNT_CONFIG_PATH).parent() {
        crate::utils::ensure_dir_exists(parent.to_string_lossy().as_ref())?;
    }

    fs::write(UMOUNT_CONFIG_PATH, content)?;
    Ok(())
}

/// Apply every persisted umount config entry to the kernel list.
pub fn load_umount_config() -> Result<()> {
    let config = read_config();
    let mut count = 0;

    for (path, flags) in &config.paths {
        ksucalls::umount_list_add(path, *flags)?;
        count += 1;
    }

    info!("Loaded {count} umount entries from config");
    Ok(())
}

/// Print the configured umount paths as JSON, matching the kernel list format.
pub fn list_umount() {
    let config = read_config();

    let mut entries: Vec<_> = config
        .paths
        .iter()
        .map(|(path, flags)| serde_json::json!({ "path": path, "flags": flags }))
        .collect();
    entries.sort_by(|a, b| a["path"].as_str().cmp(&b["path"].as_str()));

    println!("{}", Value::Array(entries));
}

pub fn add_umount(target_path: &str, flags: u32) -> Result<()> {
    let mut config = read_config();
    config.paths.insert(target_path.to_string(), flags);
    write_config(&config)
}

pub fn del_umount(target_path: &str) -> Result<()> {
    let mut config = read_config();
    if config.paths.remove(target_path).is_some() {
        write_config(&config)?;
    }
    Ok(())
}

pub fn wipe_umount() -> Result<()> {
    write_config(&Config::default())
}
