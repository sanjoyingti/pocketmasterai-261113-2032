package com.pocketmaster.ai.provider

/**
 * 13 tool JSON Schema definitions that mirror pocket-master-mcp server tools.
 * The orchestrator passes these to whatever LLM is active; the LLM returns
 * a tool call, and AgentToolExecutor validates + executes it against the device.
 */
object AgentToolDefinitions {

    /** Full tool set as one JSON string (OpenAI / Gemini format). */
    private val toolsJson = """
        [
          {
            "name": "get_device_state",
            "description": "Read the current Pocket Master state: chain order, block on/off, model codes, param values, preset slot and global volume.",
            "parameters": {
              "type": "object",
              "properties": {},
              "required": []
            }
          },
          {
            "name": "list_presets",
            "description": "List all saved preset slots (P01-P50, F01-F50) with their names.",
            "parameters": {
              "type": "object",
              "properties": {},
              "required": []
            }
          },
          {
            "name": "select_preset",
            "description": "Load a preset into the active slot P01-P50/F01-F50.",
            "parameters": {
              "type": "object",
              "properties": {
                "slot": { "type": "string", "description": "Preset slot to select, e.g. P01" }
              },
              "required": ["slot"]
            }
          },
          {
            "name": "set_block_state",
            "description": "Turn a block on or off by its chain position 0-8.",
            "parameters": {
              "type": "object",
              "properties": {
                "pos": { "type": "integer", "description": "Chain position 0-8" },
                "on": { "type": "boolean", "description": "true = bypass off (on), false = bypass on (off)" }
              },
              "required": ["pos", "on"]
            }
          },
          {
            "name": "set_block_model",
            "description": "Set the model inside a block by name using the exact device_map.json model name.",
            "parameters": {
              "type": "object",
              "properties": {
                "pos": { "type": "integer", "description": "Chain position 0-8" },
                "model": { "type": "string", "description": "Exact model name from device_map.json" },
                "on": { "type": "boolean" }
              },
              "required": ["pos", "model"]
            }
          },
          {
            "name": "set_parameter",
            "description": "Set a numeric parameter of a block (Gain, Bass, Mid, Treble, Presence, Vol, Drive, Tone, Mix, Time, FBack, Decay, Depth, Rate, THRE, Mode, +3dB).",
            "parameters": {
              "type": "object",
              "properties": {
                "pos": { "type": "integer", "description": "Chain position 0-8" },
                "param": { "type": "string", "description": "Parameter key, e.g. Gain" },
                "value": { "type": "number", "description": "Numeric value within the model's valid range" }
              },
              "required": ["pos", "param", "value"]
            }
          },
          {
            "name": "reorder_signal_chain",
            "description": "Rearrange the 9-slot signal chain. Fixed block DRV-AMP-IR-EQ (positions 2,3,4,5) must stay together and never be split.",
            "parameters": {
              "type": "object",
              "properties": {
                "order": { "type": "array", "items": { "type": "integer", "minimum": 0, "maximum": 8 }, "description": "Full 9-position chain order" }
              },
              "required": ["order"]
            }
          },
          {
            "name": "apply_full_tone_preset",
            "description": "Apply a complete tone: reorder chain, set block models/states/params, set volume. The preset JSON must use only model names and param values from device_map.json.",
            "parameters": {
              "type": "object",
              "properties": {
                "presetJson": { "type": "string", "description": "JSON describing full chain, models, states, params and volume" }
              },
              "required": ["presetJson"]
            }
          },
          {
            "name": "save_preset",
            "description": "Save the current edit buffer to a preset slot P01-P50/F01-F50 with a name.",
            "parameters": {
              "type": "object",
              "properties": {
                "slot": { "type": "string", "description": "Target slot P01-P50/F01-F50" },
                "name": { "type": "string", "description": "Preset display name (<=10 chars)" }
              },
              "required": ["slot", "name"]
            }
          },
          {
            "name": "rename_preset",
            "description": "Rename an existing saved preset slot.",
            "parameters": {
              "type": "object",
              "properties": {
                "old": { "type": "string", "description": "Old preset name" },
                "new": { "type": "string", "description": "New preset name" }
              },
              "required": ["old", "new"]
            }
          },
          {
            "name": "backup_all_presets",
            "description": "Export all 100 preset slots to JSON for backup.",
            "parameters": {
              "type": "object",
              "properties": {},
              "required": []
            }
          },
          {
            "name": "set_global_levels",
            "description": "Set global device levels: master volume, input, fxRec (record out), monitor and btRec (Bluetooth record).",
            "parameters": {
              "type": "object",
              "properties": {
                "masterVol": { "type": "number", "minimum": 0, "maximum": 1 },
                "input": { "type": "number", "minimum": 0, "maximum": 1 },
                "fxRec": { "type": "number", "minimum": 0, "maximum": 1 },
                "monitor": { "type": "number", "minimum": 0, "maximum": 1 },
                "btRec": { "type": "number", "minimum": 0, "maximum": 1 }
              },
              "required": ["masterVol"]
            }
          },
          {
            "name": "web_search",
            "description": "Search the web (DuckDuckGo by default, Google CSE if configured) for guitar rig/solo tone references. Returns title, link and snippet for each result.",
            "parameters": {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "Search query, e.g. 'Sultans of Swing guitar tone rig settings'" }
              },
              "required": ["query"]
            }
          }
        ]
        """.trimIndent()

    /** The single shared JSON string for all providers. */
    val toolsJsonSchema: String get() = toolsJson
}
