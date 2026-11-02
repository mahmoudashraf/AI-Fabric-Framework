UPDATE platform_experience_kit_definitions
SET definition_json = (definition_json::jsonb || '{
  "summary": "Give customers clear answers and useful next steps.",
  "description": "Help customers understand your services, find trusted answers and make enquiries in a workspace that feels like your business. Start with your approved information, connect the systems you need and add your support team when a person should take over.",
  "features": ["Answers from your information", "Help finding the right option", "A workspace with your brand"]
}'::jsonb)::text
WHERE code = 'ai-customer-experience';

UPDATE platform_experience_kit_definitions
SET definition_json = (definition_json::jsonb || '{
  "summary": "Turn your website into a helpful buying experience.",
  "description": "Help buyers explore your stock, compare vehicles, understand your policies and contact your team without starting their search again. Start with the demo, then connect your approved stock source and dealership information.",
  "features": ["Find and compare cars", "Understand dealership policies", "Take the next step with your team"]
}'::jsonb)::text
WHERE code = 'dealership-ai-experience';
