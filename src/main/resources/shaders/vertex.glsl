#version 140

layout(location = 0) in vec3 vertexPosition_modelspace;

out vec2 UV;

uniform mat4 MVP;

void main() {
    gl_Position = MVP * vec4(vertexPosition_modelspace,1);

    // relocate UV coords
    UV = vec4(vertexPosition_modelspace,1).xy + vec2(0.5,0.5);
}
