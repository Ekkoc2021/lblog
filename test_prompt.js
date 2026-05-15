const http = require('https');
const sysPrompt = `You are an expert diagram creation assistant specializing in draw.io XML generation.
Your primary function is chat with user and crafting clear, well-organized visual diagrams through precise XML specifications.

When you are asked to create a diagram, briefly describe your plan about the layout and structure (2-3 sentences max), then use display_diagram tool to generate the XML.
After generating or editing a diagram, you don't need to say anything. The user can see the diagram - no need to describe it.

You have one tool: display_diagram. It generates draw.io XML for diagrams. Always call it when asked to draw.

Common styles:
- Shapes: rounded=1, fillColor=#hex, strokeColor=#hex
- Edges: endArrow=classic/block/open/none
- Text: fontSize=14, fontStyle=1 (bold)

Current diagram XML:
\`\`\`xml
<empty>
\`\`\``;

const data = JSON.stringify({
  model: 'deepseek-chat',
  messages: [
    {role: 'system', content: sysPrompt},
    {role: 'user', content: '画一个微服务架构图'}
  ],
  tools: [{
    type: 'function',
    function: {
      name: 'display_diagram',
      description: 'Generate draw.io XML for a diagram. Call this when user asks to draw a diagram.',
      parameters: { type: 'object', properties: { xml: { type: 'string' } }, required: ['xml'] }
    }
  }]
});

const req = http.request('https://api.deepseek.com/v1/chat/completions', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer sk-5bc99d693c42462fb6b8abfeea207726'
  }
}, (res) => {
  let body = '';
  res.on('data', (chunk) => body += chunk);
  res.on('end', () => {
    const obj = JSON.parse(body);
    const choice = obj.choices?.[0];
    console.log('Finish reason:', choice?.finish_reason);
    console.log('Has tool_calls:', !!choice?.message?.tool_calls);
    if (choice?.message?.tool_calls) {
      console.log('Tool name:', choice.message.tool_calls[0].function.name);
    } else {
      console.log('Content:', (choice?.message?.content || '').substring(0, 300));
    }
  });
});
req.write(data);
req.end();
